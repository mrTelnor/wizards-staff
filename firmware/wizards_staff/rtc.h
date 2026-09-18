#pragma once
#include <Wire.h>
#include "config.h"
#include "log.h"

// Часы реального времени DS3231 на модуле ZS-042. Модуль висит на шине I2C и держит время
// от своей батарейки, пока посох выключен. Точность у DS3231 хорошая, пара минут в год.
//
// Своей библиотеки не берём: у часов всего семь регистров времени, и работать с ними
// напрямую понятнее, чем разбираться в чужой обёртке. Числа внутри часов хранятся в BCD:
// каждый десятичный разряд занимает свои четыре бита, то есть число 25 лежит как 0x25.
//
// Время везде в секундах от 1 января 1970 года по UTC (так называемый epoch), как в протоколе
// обмена с телефоном. Местное время получается прибавлением TZ_OFFSET_MINUTES и нужно
// только чтобы напечатать время человеку.

bool rtcPresent = false;   // часы отозвались на шине
bool rtcValid   = false;   // время в часах осмысленное, а не мусор после потери питания

// ---- мелкие переводчики ----

static uint8_t rtcFromBcd(uint8_t v) { return (v >> 4) * 10 + (v & 0x0F); }
static uint8_t rtcToBcd(uint8_t v)   { return ((v / 10) << 4) | (v % 10); }

// Сколько дней прошло от 1 января 1970 года до заданной даты. Формула из стандартной
// библиотеки C++ (алгоритм days_from_civil), сама учитывает високосные годы.
static long rtcDaysFromDate(long y, unsigned m, unsigned d) {
  y -= m <= 2;                                    // март считаем началом года, тогда 29 февраля в конце
  long era = (y >= 0 ? y : y - 399) / 400;        // номер четырёхсотлетия
  unsigned yoe = (unsigned)(y - era * 400);       // год внутри четырёхсотлетия, 0..399
  unsigned doy = (153 * (m + (m > 2 ? -3 : 9)) + 2) / 5 + d - 1;   // день внутри года
  unsigned doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;            // день внутри четырёхсотлетия
  return era * 146097 + (long)doe - 719468;       // 719468 это сдвиг к 1970 году
}

// Обратный перевод: из числа дней от 1970 года в год, месяц и день.
static void rtcDateFromDays(long z, int& y, unsigned& m, unsigned& d) {
  z += 719468;
  long era = (z >= 0 ? z : z - 146096) / 146097;
  unsigned doe = (unsigned)(z - era * 146097);
  unsigned yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365;
  long year = (long)yoe + era * 400;
  unsigned doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
  unsigned mp = (5 * doy + 2) / 153;
  d = doy - (153 * mp + 2) / 5 + 1;
  m = mp + (mp < 10 ? 3 : -9);
  y = (int)(year + (m <= 2));
}

// ---- разговор с микросхемой ----

// Читает подряд count регистров начиная с адреса reg. Возвращает false, если часы не ответили.
static bool rtcRead(uint8_t reg, uint8_t* buf, uint8_t count) {
  Wire.beginTransmission(I2C_ADDR_RTC);
  Wire.write(reg);
  if (Wire.endTransmission() != 0) return false;
  if (Wire.requestFrom((int)I2C_ADDR_RTC, (int)count) != count) return false;
  for (uint8_t i = 0; i < count; i++) buf[i] = Wire.read();
  return true;
}

// Пишет один регистр.
static bool rtcWrite(uint8_t reg, uint8_t value) {
  Wire.beginTransmission(I2C_ADDR_RTC);
  Wire.write(reg);
  Wire.write(value);
  return Wire.endTransmission() == 0;
}

// Записывает время в часы и отмечает, что оно теперь настоящее.
// epoch: секунды от 1970 года по UTC, как присылает телефон.
bool rtcSetEpoch(long epoch) {
  if (!rtcPresent) return false;

  long days = epoch / 86400;
  long secs = epoch % 86400;
  if (secs < 0) { secs += 86400; days--; }        // на всякий случай, если время до 1970 года

  int      year;
  unsigned month, day;
  rtcDateFromDays(days, year, month, day);
  uint8_t weekday = (uint8_t)((days + 4) % 7) + 1;   // 1 января 1970 был четверг

  Wire.beginTransmission(I2C_ADDR_RTC);
  Wire.write(0x00);
  Wire.write(rtcToBcd(secs % 60));
  Wire.write(rtcToBcd((secs / 60) % 60));
  Wire.write(rtcToBcd(secs / 3600));               // старший бит 0: часы в 24-часовом режиме
  Wire.write(weekday);
  Wire.write(rtcToBcd((uint8_t)day));
  Wire.write(rtcToBcd((uint8_t)month));            // старший бит 0: век двадцать первый
  Wire.write(rtcToBcd((uint8_t)(year - 2000)));
  if (Wire.endTransmission() != 0) return false;

  // Регистр состояния 0x0F: старший бит взводится сам, когда часы теряли питание.
  // Сбрасываем его, это наша отметка «время выставлено».
  uint8_t status = 0;
  if (rtcRead(0x0F, &status, 1)) rtcWrite(0x0F, status & 0x7F);
  rtcValid = true;
  return true;
}

// Текущее время часов в секундах от 1970 года по UTC.
// Возвращает 0, если часов нет или время в них не выставлено: по протоколу это значит
// «времени не знаю», и телефон подставит своё.
long rtcEpoch() {
  if (!rtcPresent || !rtcValid) return 0;

  uint8_t r[7];
  if (!rtcRead(0x00, r, 7)) return 0;

  uint8_t hours;
  if (r[2] & 0x40) {                               // часы в 12-часовом режиме
    hours = rtcFromBcd(r[2] & 0x1F);
    if (hours == 12) hours = 0;
    if (r[2] & 0x20) hours += 12;                  // отметка «после полудня»
  } else {
    hours = rtcFromBcd(r[2] & 0x3F);
  }

  int      year  = 2000 + rtcFromBcd(r[6]);
  unsigned month = rtcFromBcd(r[5] & 0x1F);
  unsigned day   = rtcFromBcd(r[4] & 0x3F);
  long days = rtcDaysFromDate(year, month, day);
  return days * 86400L + hours * 3600L + rtcFromBcd(r[1] & 0x7F) * 60L + rtcFromBcd(r[0] & 0x7F);
}

// Складывает строку «2026-09-17 21:45:03» из времени epoch, переведённого в местный пояс.
// Если времени нет (epoch равен нулю), пишет прочерки.
void rtcFormat(long epoch, char* out, size_t len) {
  if (epoch == 0) { snprintf(out, len, "--:--:--"); return; }

  long local = epoch + (long)TZ_OFFSET_MINUTES * 60;
  long days  = local / 86400;
  long secs  = local % 86400;
  if (secs < 0) { secs += 86400; days--; }

  int      year;
  unsigned month, day;
  rtcDateFromDays(days, year, month, day);
  snprintf(out, len, "%04d-%02u-%02u %02ld:%02ld:%02ld",
           year, month, day, secs / 3600, (secs / 60) % 60, secs % 60);
}

// Время сборки прошивки в секундах от 1970 года по UTC. Компилятор подставляет свои
// __DATE__ и __TIME__ в местном времени компьютера, поэтому вычитаем часовой пояс.
static long rtcBuildEpoch() {
  const char* months = "JanFebMarAprMayJunJulAugSepOctNovDec";
  char  name[4] = { __DATE__[0], __DATE__[1], __DATE__[2], 0 };
  int   day     = atoi(__DATE__ + 4);
  int   year    = atoi(__DATE__ + 7);
  unsigned month = (unsigned)((strstr(months, name) - months) / 3 + 1);
  int hh = atoi(__TIME__), mm = atoi(__TIME__ + 3), ss = atoi(__TIME__ + 6);
  return rtcDaysFromDate(year, month, day) * 86400L + hh * 3600L + mm * 60L + ss
         - (long)TZ_OFFSET_MINUTES * 60;
}

// Поднимает шину I2C и часы. Вызывать один раз в setup().
// Если часы потеряли питание (батарейка села или её не было), в них кладётся время сборки
// прошивки: лучше примерное время, чем январь 2000 года. Точное придёт с телефона командой time.
void rtcBegin() {
  Wire.begin(PIN_I2C_SDA, PIN_I2C_SCL);

  Wire.beginTransmission(I2C_ADDR_RTC);
  rtcPresent = (Wire.endTransmission() == 0);
  if (!rtcPresent) {
    logLine(LOG_TIME, LOG_ERROR, "DS3231 не отвечает, проверь провода SDA, SCL и питание");
    return;
  }

  uint8_t status = 0;
  rtcRead(0x0F, &status, 1);
  bool powerLost = status & 0x80;
  rtcValid = !powerLost;

  if (powerLost) {
    logLine(LOG_TIME, LOG_WARN, "питание терялось, время неизвестно, ставлю время сборки прошивки");
    rtcSetEpoch(rtcBuildEpoch());
  }

  logSetTime(rtcEpoch());   // с этого места у журнала есть настоящее время
  char buf[24];
  rtcFormat(rtcEpoch(), buf, sizeof(buf));
  logLine(LOG_TIME, LOG_INFO, "DS3231 найден, время %s%s", buf, powerLost ? " (примерное)" : "");
}
