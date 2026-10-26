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
// Служебные кнопки на втором расширителе пока только ищутся при старте, их опрос будет позже.

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
