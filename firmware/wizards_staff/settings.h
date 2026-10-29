#pragma once
#include <Preferences.h>
#include "config.h"
#include "display.h"
#include "sound.h"

// Настройки из меню посоха: что выбрано, как это хранится во флеш и как применяется.
// Само меню (экран и кнопки) — в menu.h, здесь только значения.
//
// Каждая настройка — номер ступени в своей таблице из config.h, так во флеш лежит
// по байту на настройку, а таблицы можно поменять, не трогая сохранённое.

struct Settings {
  uint8_t bright;    // ступень в BRIGHT_LEVELS
  uint8_t volume;    // ступень в VOLUME_LEVELS
  uint8_t clock;     // ступень в CLOCK_TIMEOUTS_MS: когда результат, история и меню возвращаются к часам
  uint8_t sleep;     // ступень в SLEEP_TIMEOUTS_MS: когда экран засыпает
  bool    testD20;   // тестовый режим d20: подмена 20 и 1 по кругу
};

Settings settings = { BRIGHT_DEFAULT, VOLUME_DEFAULT, CLOCK_DEFAULT, SLEEP_DEFAULT, TEST_D20_CYCLE };

// Сколько ждать до возврата к часам и до сна, в миллисекундах.
unsigned long settingsClockMs() { return CLOCK_TIMEOUTS_MS[settings.clock]; }
unsigned long settingsSleepMs() { return SLEEP_TIMEOUTS_MS[settings.sleep]; }

// Пара «возврат к часам, сон» допустима, если сон строго дольше возврата
// (решение автора 2026-10-29): иначе экран засыпал бы раньше, чем вернулся к часам.
bool settingsPairOk(int clock, int sleep) {
  return CLOCK_TIMEOUTS_MS[clock] < SLEEP_TIMEOUTS_MS[sleep];
}

// Применяет настройки к железу: яркость подсветки, громкость, пометку тестового режима.
// Таймауты отдельно применять не нужно, главный цикл берёт их через settingsClockMs и settingsSleepMs.
void settingsApply() {
  displaySetBrightness(BRIGHT_LEVELS[settings.bright]);
  soundVolumePct  = VOLUME_LEVELS[settings.volume];
  displayTestMark = settings.testD20;
}

// Достаёт настройки из флеш. Испорченные или несовместимые значения (ступень за
// краем таблицы, сон не дольше возврата) заменяются значениями по умолчанию.
// Вызывать в setup() до первого экрана и первого звука.
void settingsLoad() {
  Preferences prefs;
  prefs.begin("staff", true);
  settings.bright  = prefs.getUChar("bright", BRIGHT_DEFAULT);
  settings.volume  = prefs.getUChar("volume", VOLUME_DEFAULT);
  settings.clock   = prefs.getUChar("clockto", CLOCK_DEFAULT);
  settings.sleep   = prefs.getUChar("sleepto", SLEEP_DEFAULT);
  settings.testD20 = prefs.getBool("testd20", TEST_D20_CYCLE);
  prefs.end();
  if (settings.bright >= BRIGHT_COUNT) settings.bright = BRIGHT_DEFAULT;
  if (settings.volume >= VOLUME_COUNT) settings.volume = VOLUME_DEFAULT;
  if (settings.clock >= CLOCK_COUNT || settings.sleep >= SLEEP_COUNT
      || !settingsPairOk(settings.clock, settings.sleep)) {
    settings.clock = CLOCK_DEFAULT;
    settings.sleep = SLEEP_DEFAULT;
  }
}

// Записывает все настройки во флеш. Зовётся по «ОК» в меню, то есть редко.
void settingsSave() {
  Preferences prefs;
  prefs.begin("staff", false);
  prefs.putUChar("bright", settings.bright);
  prefs.putUChar("volume", settings.volume);
  prefs.putUChar("clockto", settings.clock);
  prefs.putUChar("sleepto", settings.sleep);
  prefs.putBool("testd20", settings.testD20);
  prefs.end();
}

// Таймаут человеческими словами для экрана: «30 S», «1 MIN», «1.5 MIN».
void settingsFormatMs(unsigned long ms, char* out, size_t len) {
  if (ms < 60000) snprintf(out, len, "%lu S", ms / 1000);
  else if (ms % 60000 == 0) snprintf(out, len, "%lu MIN", ms / 60000);
  else snprintf(out, len, "%lu.%lu MIN", ms / 60000, (ms % 60000) / 6000);
}
