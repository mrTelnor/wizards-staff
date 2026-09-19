#pragma once
#include <Arduino.h>
#include <stdarg.h>
#include <time.h>
#include "config.h"

// Единый вид служебных сообщений. Одна функция на всю прошивку: она клеит время,
// имя модуля и уровень, печатает в монитор порта и отдаёт ту же строку в приложение.
//
// Вид строки:
//   2026-09-18 18:27:22.104 TIME       info: DS3231 найден, время 2026-09-18 18:27:22
//   2026-09-18 18:27:22.140 FS       ERROR!: раздел не поднялся после форматирования
//
// Уровень выровнен по правому краю, поэтому ERROR! торчит из столбца и виден при
// беглом просмотре. Имена модулей латиницей, сами сообщения по-русски.

enum LogLevel { LOG_INFO, LOG_WARN, LOG_ERROR };

// Имена модулей. Держим тут списком, чтобы опечатка в вызове ловилась компилятором,
// а не расползалась по логу. Расшифровка для человека - на экране «Посох» в приложении.
static const char* LOG_BOOT    = "BOOT";      // запуск, версия прошивки
static const char* LOG_FS      = "FS";        // раздел под файлы LittleFS
static const char* LOG_HISTORY = "HISTORY";   // история бросков во флеш
static const char* LOG_DICE    = "DICE";      // таблица костей и самопроверка генератора
static const char* LOG_TIME    = "TIME";      // часы DS3231
static const char* LOG_BLE     = "BLE";       // связь с планшетом
static const char* LOG_ID      = "ID";        // номер посоха
static const char* LOG_STRIKE  = "STRIKE";    // датчик удара и решения по удару
static const char* LOG_CHARGE  = "CHARGE";    // взвод посоха
static const char* LOG_ROLL    = "ROLL";      // сам бросок
static const char* LOG_BATTERY = "BATTERY";   // заряд аккумулятора
static const char* LOG_AUTH    = "AUTH";      // PIN и защита подключения

// Куда отдавать строку кроме монитора порта. Ставится и снимается из главного скетча,
// когда планшет подключается и отключается. Через указатель, а не прямым вызовом bleSendLog,
// иначе log.h и ble.h включали бы друг друга.
//
// Пустой указатель значит «планшета нет»: такая строка уходит только в монитор порта.
// Журнал загрузки в приложение не попадает и не копится - до подключения планшета
// он весь печатается в первые полсекунды, а плата на этом этапе и так подключена к ПК.
// Буфер на 30 строк, который раньше сливался при подключении, убран 2026-09-19:
// около 3 КБ оперативной памяти ради того, что и так видно в мониторе порта.
void (*logSink)(const char*) = nullptr;

// Время, от которого считаем: пара «сколько было на часах» и «сколько было на millis()».
// Секунды берём отсюда, а не из DS3231 при каждой строке: чтение по I2C занимает около
// миллисекунды, а логов бывает много. Пара обновляется при каждом чтении часов, поэтому
// разойтись они успевают разве что на доли секунды.
uint32_t      logBaseEpoch  = 0;
unsigned long logBaseMillis = 0;

// Сообщает журналу текущее время. Зовётся оттуда, где часы и так читаются.
//
// Сдвигаем отсчёт только когда часы реально разошлись с нашим счётом больше чем на пару
// секунд. Если подтягивать при каждом вызове, отсчёт миллисекунд обнуляется, и строка,
// напечатанная сразу после подтяжки, всегда получает ".000". Именно так и было до
// 2026-09-18: у всех строк заряда стояли нули. Кварц платы уходит на доли секунды за вечер,
// так что между подтяжками время не успевает испортиться.
void logSetTime(uint32_t epoch) {
  if (!epoch) return;
  if (logBaseEpoch) {
    uint32_t mine = logBaseEpoch + (millis() - logBaseMillis) / 1000;
    uint32_t diff = mine > epoch ? mine - epoch : epoch - mine;
    if (diff < 2) return;
  }
  logBaseEpoch  = epoch;
  logBaseMillis = millis();
}

// Собирает отметку времени шириной 23 символа.
// Часы известны - местное время. Неизвестны - время от включения со знаком плюс,
// чтобы нельзя было спутать одно с другим.
static void logTimestamp(char* out, size_t len) {
  unsigned long up = millis();
  if (logBaseEpoch) {
    unsigned long since = up - logBaseMillis;
    time_t t = (time_t)(logBaseEpoch + since / 1000) + TZ_OFFSET_MINUTES * 60;
    struct tm tmv;
    gmtime_r(&t, &tmv);
    char date[24];
    strftime(date, sizeof(date), "%Y-%m-%d %H:%M:%S", &tmv);
    snprintf(out, len, "%s.%03lu", date, (unsigned long)(since % 1000));
  } else {
    // 13 значащих символов плюс 10 пробелов: ширина та же 23, столбцы не разъезжаются
    snprintf(out, len, "+%02lu:%02lu:%02lu.%03lu          ",
             up / 3600000UL, (up / 60000UL) % 60, (up / 1000UL) % 60, up % 1000UL);
  }
}

// Собирает и печатает строку. toApp говорит, отдавать ли её ещё и в приложение.
// Отсекает с конца оборванный символ UTF-8. Русская буква занимает два байта, и snprintf,
// обрезая строку по размеру буфера, может рассечь её пополам: вместо буквы получается
// половинка, и приложение рисует на её месте ромбик с вопросом. Здесь отступаем назад
// до последнего целого символа. У продолжающих байтов старшие биты 10, у первого байта
// символа - 11, по ним и определяем, сколько байт символ обязан занимать.
static void logTrimBrokenUtf8(char* s) {
  size_t len = strlen(s);
  if (len == 0) return;
  size_t k = len - 1;
  while (k > 0 && ((unsigned char)s[k] & 0xC0) == 0x80) k--;
  unsigned char lead = (unsigned char)s[k];
  size_t need = lead < 0x80 ? 1 : lead >= 0xF0 ? 4 : lead >= 0xE0 ? 3 : lead >= 0xC0 ? 2 : 1;
  if (len - k < need) s[k] = 0;   // символ начался, но целиком не поместился
}

static void logEmit(const char* module, LogLevel level, const char* text, bool toApp) {
  char stamp[24];
  logTimestamp(stamp, sizeof(stamp));
  const char* lv = level == LOG_ERROR ? "ERROR!" : (level == LOG_WARN ? "warn" : "info");

  char line[256];
  snprintf(line, sizeof(line), "%s %-7s %6s: %s", stamp, module, lv, text);
  logTrimBrokenUtf8(line);   // строку могло обрезать по размеру буфера, см. комментарий выше

  Serial.println(line);
  if (!toApp) return;

  if (logSink) logSink(line);
}

// Пишет строку журнала в монитор порта и в приложение. Формат как у printf.
void logLine(const char* module, LogLevel level, const char* fmt, ...) {
  char text[200];
  va_list args;
  va_start(args, fmt);
  vsnprintf(text, sizeof(text), fmt, args);
  va_end(args);
  logEmit(module, level, text, true);
}

// То же самое, но только в монитор порта. Для того, что у приложения уже есть своим каналом:
// заряд приходит туда событием bat, и дублировать его строкой журнала значит гонять один факт
// дважды по узкому каналу BLE и засорять экран логов.
void logLocal(const char* module, LogLevel level, const char* fmt, ...) {
  char text[200];
  va_list args;
  va_start(args, fmt);
  vsnprintf(text, sizeof(text), fmt, args);
  va_end(args);
  logEmit(module, level, text, false);
}
