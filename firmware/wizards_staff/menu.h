#pragma once
#include "config.h"
#include "settings.h"
#include "display.h"
#include "sound.h"
#include "battery.h"
#include "history.h"
#include "identity.h"
#include "rtc.h"

// Меню посоха на кнопке «ОК» (SPEC 2.5). Здесь экран меню и что делают в нём кнопки
// ОК, ◄, ►, Отмена. Сами значения и их хранение — в settings.h.
//
// Два уровня: список пунктов и правка одного пункта. В списке ◄ ► выбирают пункт,
// ОК открывает его, Отмена выходит из меню. В правке ◄ ► меняют значение, и оно
// сразу видно и слышно; ОК сохраняет во флеш, Отмена возвращает прежнее.
// Выход из меню по времени или любым другим путём (кость, удар, сон) тоже возвращает
// несохранённое: см. menuLeave.

enum MenuItem { MENU_BRIGHT, MENU_VOLUME, MENU_CLOCK, MENU_SLEEP, MENU_TEST, MENU_INFO, MENU_COUNT };
const char* const MENU_NAMES[MENU_COUNT] = { "BRIGHTNESS", "VOLUME", "CLOCK", "SLEEP", "TEST D20", "INFO" };

const int MENU_HEADER_Y = 30;
const int MENU_ROW_Y    = 56;    // верх первой строки
const int MENU_ROW_H    = 26;
const int MENU_HINT_Y   = 222;   // нижняя строка мелко: подсказка или ступени таймаута

bool          menuActive  = false;   // меню открыто (может быть, уже ушли с его экрана — см. menuLeave)
int           menuSel     = 0;       // выбранный пункт
bool          menuEditing = false;   // правим значение выбранного пункта
bool          menuInfo    = false;   // открыт экран «Сведения»
Settings      menuBefore;            // настройки до начала правки: их вернёт Отмена
unsigned long menuAt      = 0;       // последнее нажатие в меню: от него считается выход по времени

// Значение пункта для правой колонки.
void menuValueText(int item, char* out, size_t len) {
  switch (item) {
    case MENU_BRIGHT: snprintf(out, len, "%d%%", (settings.bright + 1) * 100 / BRIGHT_COUNT); break;
    case MENU_VOLUME: snprintf(out, len, "%s", VOLUME_NAMES[settings.volume]); break;
    case MENU_CLOCK:  settingsFormatMs(settingsClockMs(), out, len); break;
    case MENU_SLEEP:  settingsFormatMs(settingsSleepMs(), out, len); break;
    case MENU_TEST:   snprintf(out, len, "%s", settings.testD20 ? "ON" : "OFF"); break;
    default:          snprintf(out, len, ">"); break;
  }
}

// Одна строка списка: имя слева, значение справа. Выбранная на тёмно-синем фоне,
// значение в правке жёлтое в стрелках «< 80% >». Строка закрашивает свой фон сама.
void drawMenuRow(int item) {
  int y = MENU_ROW_Y + item * MENU_ROW_H;
  bool selected = item == menuSel;
  uint16_t bg = selected ? COLOR_SELECT : ST77XX_BLACK;
  tft.fillRect(0, y - 4, TFT_SIZE, MENU_ROW_H - 2, bg);
  tft.setTextSize(2);
  tft.setTextColor(ST77XX_WHITE, bg);
  tft.setCursor(8, y);
  tft.print(MENU_NAMES[item]);

  char value[16], shown[20];
  menuValueText(item, value, sizeof(value));
  bool editing = selected && menuEditing;
  snprintf(shown, sizeof(shown), editing ? "< %s >" : "%s", value);
  uint16_t color = editing ? ST77XX_YELLOW
                 : item == MENU_TEST && settings.testD20 ? ST77XX_ORANGE : ST77XX_CYAN;
  tft.setTextColor(color, bg);
  tft.setCursor(TFT_SIZE - 8 - (12 * (int)strlen(shown) - 2), y);   // прижато вправо
  tft.print(shown);
}

// Ступень таймаута коротко для нижней строки: «30S», «1M», «1.5M».
void menuShortMs(unsigned long ms, char* out, size_t len) {
  if (ms < 60000) snprintf(out, len, "%luS", ms / 1000);
  else if (ms % 60000 == 0) snprintf(out, len, "%luM", ms / 60000);
  else snprintf(out, len, "%lu.%luM", ms / 60000, (ms % 60000) / 6000);
}

// Нижняя строка. В правке возврата к часам и сна — все ступени: выбранная жёлтая,
// доступные белые, недоступные серые (сон обязан быть дольше возврата). В остальных
// случаях — подсказка по кнопкам.
void drawMenuHint() {
  tft.fillRect(0, MENU_HINT_Y, TFT_SIZE, 10, ST77XX_BLACK);
  tft.setTextSize(1);
  bool steps = menuEditing && (menuSel == MENU_CLOCK || menuSel == MENU_SLEEP);
  if (!steps) {
    const char* hint = menuEditing ? "< > CHANGE   OK SAVE   CANCEL UNDO" : "OK OPEN   CANCEL EXIT";
    tft.setTextColor(COLOR_GREY, ST77XX_BLACK);
    tft.setCursor((TFT_SIZE - 6 * (int)strlen(hint)) / 2, MENU_HINT_Y);
    tft.print(hint);
    return;
  }
  bool clock = menuSel == MENU_CLOCK;
  int count = clock ? CLOCK_COUNT : SLEEP_COUNT;
  char parts[6][8];
  int width = 0;
  for (int i = 0; i < count; i++) {
    menuShortMs(clock ? CLOCK_TIMEOUTS_MS[i] : SLEEP_TIMEOUTS_MS[i], parts[i], sizeof(parts[i]));
    width += 6 * strlen(parts[i]) + (i ? 18 : 0);   // между ступенями три пробела
  }
  tft.setCursor((TFT_SIZE - width) / 2, MENU_HINT_Y);
  for (int i = 0; i < count; i++) {
    bool current = clock ? i == settings.clock : i == settings.sleep;
    bool ok      = clock ? settingsPairOk(i, settings.sleep) : settingsPairOk(settings.clock, i);
    tft.setTextColor(current ? ST77XX_YELLOW : ok ? ST77XX_WHITE : COLOR_DARK_GREY, ST77XX_BLACK);
    if (i) tft.print("   ");
    tft.print(parts[i]);
  }
}

// Список пунктов и нижняя строка, без стирания экрана: для каждого шага ◄ ►.
void drawMenuList() {
  for (int i = 0; i < MENU_COUNT; i++) drawMenuRow(i);
  drawMenuHint();
}

// Экран меню целиком: при входе и при возврате со «Сведений».
void screenMenu() {
  tft.fillScreen(ST77XX_BLACK);
  statusShowTime = true;
  drawStatusBar();
  drawCentered("MENU", MENU_HEADER_Y, 2, ST77XX_CYAN);
  drawMenuList();
}

// «Сведения»: прошивка, заряд, сколько бросков, номер посоха, подтверждено ли время.
// Только чтение, удобно для замеров автономности.
void screenMenuInfo() {
  tft.fillScreen(ST77XX_BLACK);
  statusShowTime = true;
  drawStatusBar();
  drawCentered("INFO", MENU_HEADER_Y, 2, ST77XX_CYAN);
  char line[32];
  tft.setTextSize(2);
  tft.setTextColor(ST77XX_WHITE, ST77XX_BLACK);
  int y = MENU_ROW_Y;
  snprintf(line, sizeof(line), "FW %s", FW_VERSION);
  tft.setCursor(8, y); tft.print(line); y += MENU_ROW_H;
  snprintf(line, sizeof(line), "BAT %d MV %d%%", batteryMillivolts(), batteryPercent());
  tft.setCursor(8, y); tft.print(line); y += MENU_ROW_H;
  snprintf(line, sizeof(line), "ROLLS %u", historyLastId());
  tft.setCursor(8, y); tft.print(line); y += MENU_ROW_H;
  snprintf(line, sizeof(line), "ID %u", staffId);
  tft.setCursor(8, y); tft.print(line); y += MENU_ROW_H;
  tft.setCursor(8, y);
  if (rtcSynced) { tft.setTextColor(ST77XX_GREEN, ST77XX_BLACK);  tft.print("TIME OK"); }
  else           { tft.setTextColor(ST77XX_ORANGE, ST77XX_BLACK); tft.print("TIME NOT CONFIRMED"); }
  y += MENU_ROW_H;
  tft.setTextSize(1);
  tft.setTextColor(COLOR_GREY, ST77XX_BLACK);
  if (!rtcSynced) { tft.setCursor(8, y); tft.print("connect the tablet to set the time"); }
  const char* hint = "OK / CANCEL BACK";
  tft.setCursor((TFT_SIZE - 6 * (int)strlen(hint)) / 2, MENU_HINT_Y);
  tft.print(hint);
}

// «ОК» вне меню: открыть его на первом пункте.
void menuOpen() {
  menuActive  = true;
  menuSel     = 0;
  menuEditing = false;
  menuInfo    = false;
  menuAt      = millis();
  screenMenu();
}

// Уйти из меню любым путём. Несохранённую правку отменяет: прежняя яркость и громкость
// возвращаются, будто правки не было. Экран не рисует, это дело того, кто уходит.
void menuLeave() {
  if (menuEditing) {
    settings = menuBefore;
    settingsApply();
  }
  menuActive  = false;
  menuEditing = false;
  menuInfo    = false;
}

// Следующая ступень в сторону dir для возврата к часам или сна, пропуская те,
// что нарушат правило «сон дольше возврата». Нет такой — остаётся прежняя.
int menuNextStep(bool clock, int from, int dir) {
  int count = clock ? CLOCK_COUNT : SLEEP_COUNT;
  for (int i = from + dir; i >= 0 && i < count; i += dir) {
    if (clock ? settingsPairOk(i, settings.sleep) : settingsPairOk(settings.clock, i)) return i;
  }
  return from;
}

// ◄ ► в правке: меняет значение выбранного пункта и сразу применяет его.
// У громкости на каждую ступень пробный писк, чтобы слышать, что выбрал.
void menuChange(int dir) {
  switch (menuSel) {
    case MENU_BRIGHT: settings.bright = constrain(settings.bright + dir, 0, BRIGHT_COUNT - 1); break;
    case MENU_VOLUME: settings.volume = constrain(settings.volume + dir, 0, VOLUME_COUNT - 1); break;
    case MENU_CLOCK:  settings.clock  = menuNextStep(true,  settings.clock, dir); break;
    case MENU_SLEEP:  settings.sleep  = menuNextStep(false, settings.sleep, dir); break;
    case MENU_TEST:   settings.testD20 = !settings.testD20; break;
  }
  settingsApply();
  drawMenuRow(menuSel);
  drawMenuHint();
  if (menuSel == MENU_VOLUME) soundNote(SOUND_TICK_HZ, 120);
}

// Разбирает короткое нажатие ОК, ◄, ► или Отмена, пока меню открыто.
// Возвращает true, когда меню закрыто и пора вернуться на взвод или к часам.
bool menuButton(int button) {
  menuAt = millis();

  if (menuInfo) {                                   // «Сведения»: ОК и Отмена возвращают в список
    if (button == BTN_OK || button == BTN_CANCEL) {
      menuInfo = false;
      screenMenu();
    }
    return false;
  }

  if (menuEditing) {
    if (button == BTN_PREV) menuChange(-1);
    if (button == BTN_NEXT) menuChange(+1);
    if (button == BTN_OK) {
      settingsSave();
      menuEditing = false;
      char value[16];
      menuValueText(menuSel, value, sizeof(value));
      logLine(LOG_BUTTONS, LOG_INFO, "меню: %s = %s, сохранено", MENU_NAMES[menuSel], value);
      drawMenuList();
    }
    if (button == BTN_CANCEL) {
      settings = menuBefore;
      settingsApply();
      menuEditing = false;
      drawMenuList();
    }
    return false;
  }

  if (button == BTN_PREV) { menuSel = (menuSel + MENU_COUNT - 1) % MENU_COUNT; drawMenuList(); }
  if (button == BTN_NEXT) { menuSel = (menuSel + 1) % MENU_COUNT; drawMenuList(); }
  if (button == BTN_OK) {
    if (menuSel == MENU_INFO) {
      menuInfo = true;
      screenMenuInfo();
    } else {
      menuBefore  = settings;
      menuEditing = true;
      drawMenuList();
    }
  }
  if (button == BTN_CANCEL) {
    menuLeave();
    return true;
  }
  return false;
}
