#pragma once
#include <Arduino.h>
#include <Wire.h>
#include "config.h"
#include "log.h"

// Кнопки костей на расширителе PCF8574 (адрес I2C_ADDR_DICE). Кнопка замыкает вход
// расширителя на землю, подтяжка у PCF8574 своя, поэтому нажатая кнопка читается нулём.
//
// Расширитель опрашиваем по шине раз в BUTTONS_POLL_MS, а не по сигналу INT: INT пришлось бы
// вести на ножку 9, а это кнопка BOOT, и посох, включённый с зажатой кнопкой, вставал бы
// в режим заливки. Одно чтение по шине занимает доли миллисекунды.
//
// Служебные кнопки и кнопки меню на втором расширителе (I2C_ADDR_SERVICE) опрашиваются так же,
// но у них два вида нажатия: короткое и долгое (LONG_PRESS_MS). Опрос — buttonsPollService().

uint8_t       btnRaw     = 0;         // как прочитали в прошлый раз, бит 1 = кнопка нажата
uint8_t       btnStable  = 0;         // устоявшееся состояние после гашения дребезга
unsigned long btnRawChangedAt[8];     // когда сырой сигнал кнопки последний раз менялся
unsigned long btnPollAt  = 0;         // когда последний раз опрашивали
bool          btnOnline  = false;     // расширитель костей отвечает

// Проверяет, отзывается ли кто-нибудь на шине по этому адресу.
bool buttonsProbe(uint8_t addr) {
  Wire.beginTransmission(addr);
  return Wire.endTransmission() == 0;
}

// Читает все восемь входов расширителя одним байтом и переворачивает биты,
// чтобы единица значила «нажата». Возвращает false, если расширитель не ответил.
bool buttonsRead(uint8_t addr, uint8_t& pressed) {
  if (Wire.requestFrom((int)addr, 1) != 1) return false;
  pressed = ~Wire.read();
  return true;
}

// Переводит все входы расширителя в режим чтения: запись единицы включает слабую подтяжку.
// После включения питания чип и так в этом режиме, но прошивка могла перезапуститься
// без выключения питания, а расширителю это не видно.
void buttonsRelease(uint8_t addr) {
  Wire.beginTransmission(addr);
  Wire.write(0xFF);
  Wire.endTransmission();
}

// Находит расширители и запоминает, какие кнопки нажаты прямо сейчас: кнопка, зажатая
// при включении, не должна сработать как нажатие. Вызывать в setup() после rtcBegin(),
// который поднимает шину I2C.
void buttonsBegin() {
  btnOnline = buttonsProbe(I2C_ADDR_DICE);
  if (btnOnline) {
    buttonsRelease(I2C_ADDR_DICE);
    buttonsRead(I2C_ADDR_DICE, btnRaw);
    btnStable = btnRaw;
    logLine(LOG_BUTTONS, LOG_INFO, "расширитель костей 0x%02X найден", I2C_ADDR_DICE);
  } else {
    logLine(LOG_BUTTONS, LOG_WARN, "расширитель костей 0x%02X не отвечает, проверь SDA, SCL, питание и перемычки адреса",
            I2C_ADDR_DICE);
  }

  if (buttonsProbe(I2C_ADDR_SERVICE)) {
    buttonsRelease(I2C_ADDR_SERVICE);
    logLine(LOG_BUTTONS, LOG_INFO, "расширитель служебных кнопок 0x%02X найден", I2C_ADDR_SERVICE);
  } else {
    logLine(LOG_BUTTONS, LOG_WARN, "расширитель служебных кнопок 0x%02X не отвечает", I2C_ADDR_SERVICE);
  }

  for (int i = 0; i < 8; i++) btnRawChangedAt[i] = 0;
}

// Опрашивать в каждом обороте loop(). Возвращает номер кнопки кости (0..7), которую
// только что нажали, или -1. Кости срабатывают сразу при нажатии, а не при отпускании:
// посох должен откликаться мгновенно. Если нажали две разом, вторая выйдет следующим опросом.
int buttonsPollDice() {
  unsigned long now = millis();
  // Пропавший расширитель (выпал провод на макетке) не дёргаем каждые 10 мс, а пробуем раз в секунду.
  unsigned long period = btnOnline ? BUTTONS_POLL_MS : BUTTONS_RETRY_MS;
  if (now - btnPollAt < period) return -1;
  btnPollAt = now;

  uint8_t raw;
  if (!buttonsRead(I2C_ADDR_DICE, raw)) {
    if (btnOnline) logLine(LOG_BUTTONS, LOG_ERROR, "расширитель костей 0x%02X пропал с шины", I2C_ADDR_DICE);
    btnOnline = false;
    return -1;
  }
  if (!btnOnline) {
    // Вернулся: начинаем с того, что нажато сейчас, чтобы не выстрелить зажатой кнопкой.
    logLine(LOG_BUTTONS, LOG_INFO, "расширитель костей 0x%02X снова на связи", I2C_ADDR_DICE);
    btnOnline = true;
    buttonsRelease(I2C_ADDR_DICE);
    btnRaw = btnStable = raw;
    return -1;
  }

  int fired = -1;
  for (int i = 0; i < 8; i++) {
    uint8_t bit = 1 << i;
    // Сырой сигнал изменился: запоминаем момент и ждём, устоится ли он.
    if ((raw ^ btnRaw) & bit) btnRawChangedAt[i] = now;
    // Держится дольше DEBOUNCE_MS и отличается от устоявшегося: настоящее нажатие или отпускание.
    if (((raw ^ btnStable) & bit) && now - btnRawChangedAt[i] >= DEBOUNCE_MS) {
      if (raw & bit) {
        if (fired >= 0) continue;   // вторую одновременную оставляем на следующий опрос
        fired = i;
      }
      btnStable ^= bit;
    }
  }
  btnRaw = raw;
  return fired;
}

// ===== Служебные кнопки и кнопки меню =====

// Имена для журнала, по номеру входа (BTN_RESET и остальные в config.h).
const char* const SERVICE_NAMES[8] = { "Сброс", "Повтор", "Свет", "Тихо", "ОК", "Назад", "Вперёд", "Отмена" };

// Что случилось с кнопкой.
// DOWN — её только что нажали: нужно, чтобы будить экран сразу, а не при отпускании.
// SHORT — отпустили раньше LONG_PRESS_MS: обычное нажатие.
// LONG — держат LONG_PRESS_MS: срабатывает сразу, не дожидаясь отпускания, чтобы было
// понятно, что пора отпускать. Отпускание после долгого нажатия молчит.
enum ServiceKind { SVC_NONE, SVC_DOWN, SVC_SHORT, SVC_LONG };

struct ServiceEvent {
  int         button;   // номер входа 0..7, -1 если ничего не случилось
  ServiceKind kind;
};

uint8_t       svcRaw       = 0;      // как прочитали в прошлый раз, бит 1 = кнопка нажата
uint8_t       svcStable    = 0;      // устоявшееся состояние после гашения дребезга
uint8_t       svcLongFired = 0;      // кнопки, у которых в этом нажатии уже было долгое
unsigned long svcRawChangedAt[8];    // когда сырой сигнал кнопки последний раз менялся
unsigned long svcPressedAt[8];       // когда кнопку нажали (устоявшееся нажатие)
unsigned long svcPollAt    = 0;
bool          svcOnline    = false;  // расширитель служебных кнопок отвечает
bool          svcSeen      = false;  // хоть раз отвечал: о первом ответе уже сказал buttonsBegin()

// Опрашивать в каждом обороте loop(). Возвращает одно событие за раз: если их больше,
// остальные выйдут следующими опросами, кнопки и время за это время никуда не денутся.
// Кнопка, зажатая при включении или при возвращении расширителя, не срабатывает.
ServiceEvent buttonsPollService() {
  ServiceEvent ev = { -1, SVC_NONE };
  unsigned long now = millis();
  unsigned long period = svcOnline ? BUTTONS_POLL_MS : BUTTONS_RETRY_MS;
  if (now - svcPollAt < period) return ev;
  svcPollAt = now;

  uint8_t raw;
  if (!buttonsRead(I2C_ADDR_SERVICE, raw)) {
    if (svcOnline) logLine(LOG_BUTTONS, LOG_ERROR, "расширитель служебных кнопок 0x%02X пропал с шины", I2C_ADDR_SERVICE);
    svcOnline = false;
    return ev;
  }
  if (!svcOnline) {
    // Нашёлся (при старте или после пропажи): начинаем с того, что нажато сейчас.
    if (svcSeen) logLine(LOG_BUTTONS, LOG_INFO, "расширитель служебных кнопок 0x%02X снова на связи", I2C_ADDR_SERVICE);
    svcSeen   = true;
    svcOnline = true;
    buttonsRelease(I2C_ADDR_SERVICE);
    svcRaw = svcStable = raw;
    svcLongFired = raw;   // зажатые сейчас не дадут ни долгого, ни короткого
    for (int i = 0; i < 8; i++) svcRawChangedAt[i] = svcPressedAt[i] = now;
    return ev;
  }

  // Сначала у всех восьми отмечаем, когда менялся сырой сигнал, и только потом ищем событие:
  // поиск останавливается на первом, и кнопки за ним иначе потеряли бы начало отсчёта дребезга.
  for (int i = 0; i < 8; i++) {
    if ((raw ^ svcRaw) & (1 << i)) svcRawChangedAt[i] = now;
  }
  svcRaw = raw;

  for (int i = 0; i < 8 && ev.button < 0; i++) {
    uint8_t bit = 1 << i;
    if (((raw ^ svcStable) & bit) && now - svcRawChangedAt[i] >= DEBOUNCE_MS) {
      svcStable ^= bit;
      if (raw & bit) {                      // нажали
        svcPressedAt[i] = now;
        svcLongFired &= ~bit;
        ev = { i, SVC_DOWN };
      } else if (!(svcLongFired & bit)) {   // отпустили, долгого не было
        ev = { i, SVC_SHORT };
      }
    } else if ((svcStable & bit) && !(svcLongFired & bit) && now - svcPressedAt[i] >= LONG_PRESS_MS) {
      svcLongFired |= bit;                  // держат долго: срабатываем один раз
      ev = { i, SVC_LONG };
    }
  }
  return ev;
}
