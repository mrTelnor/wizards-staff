#pragma once
#include <Arduino.h>

// Единственное место, где записаны номера ножек, адреса и тайминги.
// В остальном коде номеров нет, только имена отсюда. Распиновка: hardware/wiring.md.

// ===== Версия прошивки, уходит телефону в ответе info =====
const char* FW_VERSION = "0.3.1";

// ===== Ножки платы ESP32-C3 SuperMini =====
const int PIN_I2C_SDA  = 0;    // шина I2C: часы и расширители кнопок
const int PIN_I2C_SCL  = 1;
const int PIN_BATTERY  = 3;    // делитель напряжения аккумулятора
const int PIN_TFT_SCK  = 4;    // дисплей, такт SPI
const int PIN_TFT_BLK  = 5;    // дисплей, подсветка (ШИМ)
const int PIN_TFT_MOSI = 6;    // дисплей, данные SPI
const int PIN_TFT_DC   = 7;    // дисплей, команда/данные
const int PIN_BUZZER   = 8;    // пищалка через транзистор
const int PIN_BTN_INT  = 9;    // сигнал от расширителей «нажата кнопка» (пока не используется)
const int PIN_TFT_RST  = 10;   // дисплей, сброс
const int PIN_STRIKE   = 20;   // датчик вибрации SW-420, выход DO
const int PIN_RING     = 21;   // кольцо WS2812B, вход DI

// ===== Адреса на шине I2C =====
const uint8_t I2C_ADDR_DICE    = 0x20;   // PCF8574 с кнопками кубиков
const uint8_t I2C_ADDR_SERVICE = 0x21;   // PCF8574 со служебными кнопками
const uint8_t I2C_ADDR_RTC     = 0x68;   // часы DS3231
// на модуле часов сидит ещё микросхема памяти AT24C32 на 0x57, мы её не используем

// ===== Часовой пояс =====
// Часы и протокол обмена с телефоном живут во времени UTC. Это смещение нужно только
// чтобы печатать и показывать местное время. 180 минут это Москва, UTC+3.
const int TZ_OFFSET_MINUTES = 180;

// ===== Размеры =====
const int RING_LEDS    = 12;   // светодиодов в кольце
const int MAX_DICE     = 10;   // максимум кубиков в одном броске
const int HISTORY_SIZE = 5;    // сколько бросков помним

// ===== Тайминги, миллисекунды =====
const unsigned long DEBOUNCE_MS         = 30;      // кнопка должна держаться столько, чтобы засчитаться
const unsigned long LONG_PRESS_MS       = 2000;    // долгое нажатие служебной кнопки
const unsigned long ARM_TIMEOUT_MS      = 30000;   // взведённый посох без удара возвращается к часам
const unsigned long SLEEP_TIMEOUT_MS    = 180000;  // 3 минуты бездействия: экран гаснет
const unsigned long STRIKE_GUARD_MS     = 300;     // после нажатия кнопки удар не считаем
const unsigned long STRIKE_COOLDOWN_MS  = 5000;    // после броска датчик не слушаем: стол ещё дрожит, посох звенит
const unsigned long STRIKE_WINDOW_MS    = 100;     // окно, в котором считаем переключения датчика
const int           STRIKE_MIN_EDGES    = 10;       // от скольких переключений в окне пачка считается ударом
const unsigned long ROLL_ANIM_MS        = 1500;    // длительность анимации броска
const unsigned long SETTINGS_TIMEOUT_MS = 20000;   // выход из настроек и истории без нажатий
const unsigned long BATTERY_PERIOD_MS   = 10000;   // как часто мерить заряд

// ===== Аккумулятор: границы для перевода напряжения в проценты =====
const int BATTERY_FULL_MV  = 4200;   // 100 %
const int BATTERY_EMPTY_MV = 3300;   // 0 %

// ===== Кубики в порядке кнопок слева направо =====
const uint8_t DICE_SIDES[8] = { 2, 4, 6, 8, 10, 12, 20, 100 };
