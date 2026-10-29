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
const char* const MENU_NAMES[MENU_COUNT] = { "Яркость", "Звук", "К часам", "Сон", "Тест d20", "Сведения" };

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

// Значение пункта для правой колонки. Строка UTF-8 с русскими буквами: буфер out
// брать с запасом, не меньше 32 байт, чтобы snprintf не обрезал букву пополам (G67).
void menuValueText(int item, char* out, size_t len) {
  switch (item) {
    case MENU_BRIGHT: snprintf(out, len, "%d%%", (settings.bright + 1) * 100 / BRIGHT_COUNT); break;
    case MENU_VOLUME: snprintf(out, len, "%s", VOLUME_NAMES[settings.volume]); break;
    case MENU_CLOCK:  settingsFormatMs(settingsClockMs(), out, len); break;
    case MENU_SLEEP:  settingsFormatMs(settingsSleepMs(), out, len); break;
    case MENU_TEST:   snprintf(out, len, "%s", settings.testD20 ? "вкл" : "выкл"); break;
    default:          snprintf(out, len, ">"); break;
  }
}

// Одна строка списка: имя слева, значение справа. Выбранная на тёмно-синем фоне,
// значение в правке жёлтое в стрелках «< 80% >». Строка закрашивает свой фон сама,
// полоса 2·size над текстом (под Ё и Й) лежит внутри этого фона.
// Самая тесная строка — «К часам < 1,5 мин >»: имя до 92-й точки, значение со 102-й.
void drawMenuRow(int item) {
  int y = MENU_ROW_Y + item * MENU_ROW_H;
  bool selected = item == menuSel;
  uint16_t bg = selected ? COLOR_SELECT : ST77XX_BLACK;
  tft.fillRect(0, y - 4, TFT_SIZE, MENU_ROW_H - 2, bg);
  drawText(tft, 8, y, MENU_NAMES[item], 2, ST77XX_WHITE, bg);

  char value[32], shown[40];   // русские буквы по два байта: запас вдвое
  menuValueText(item, value, sizeof(value));
  bool editing = selected && menuEditing;
  snprintf(shown, sizeof(shown), editing ? "< %s >" : "%s", value);
  uint16_t color = editing ? ST77XX_YELLOW
                 : item == MENU_TEST && settings.testD20 ? ST77XX_ORANGE : ST77XX_CYAN;
  drawText(tft, TFT_SIZE - 8 - (textWidth(shown, 2) - 2), y, shown, 2, color, bg);   // прижато вправо
}

// Ступень таймаута коротко для нижней строки: «30с», «1м», «1,5м». Строка UTF-8:
// буфер out не меньше 12 байт.
void menuShortMs(unsigned long ms, char* out, size_t len) {
  if (ms < 60000) snprintf(out, len, "%luс", ms / 1000);
  else if (ms % 60000 == 0) snprintf(out, len, "%luм", ms / 60000);
  else snprintf(out, len, "%lu,%luм", ms / 60000, (ms % 60000) / 6000);
}

// Нижняя строка. В правке возврата к часам и сна — все ступени: выбранная жёлтая,
// доступные белые, недоступные серые (сон обязан быть дольше возврата). В остальных
// случаях — подсказка по кнопкам.
void drawMenuHint() {
  tft.fillRect(0, MENU_HINT_Y - 2, TFT_SIZE, 12, ST77XX_BLACK);   // с полосой над строкой
  bool steps = menuEditing && (menuSel == MENU_CLOCK || menuSel == MENU_SLEEP);
  if (!steps) {
    // Подсказка правки 37 знаков, 222 точки: в 240 влезает только с двойными пробелами.
    const char* hint = menuEditing ? "◄► менять  ОК сохранить  Отмена откат" : "ОК открыть   Отмена выход";
    drawCentered(hint, MENU_HINT_Y, 1, COLOR_GREY);
    return;
  }
  bool clock = menuSel == MENU_CLOCK;
  int count = clock ? CLOCK_COUNT : SLEEP_COUNT;
  char parts[6][12];   // «1,5м» — 6 байт с нулём, запас вдвое
  int width = 0;
  for (int i = 0; i < count; i++) {
    menuShortMs(clock ? CLOCK_TIMEOUTS_MS[i] : SLEEP_TIMEOUTS_MS[i], parts[i], sizeof(parts[i]));
    width += textWidth(parts[i], 1) + (i ? 18 : 0);   // между ступенями три пробела
  }
  int x = (TFT_SIZE - width) / 2;
  for (int i = 0; i < count; i++) {
    bool current = clock ? i == settings.clock : i == settings.sleep;
    bool ok      = clock ? settingsPairOk(i, settings.sleep) : settingsPairOk(settings.clock, i);
    if (i) x += 18;
    drawText(tft, x, MENU_HINT_Y, parts[i], 1,
             current ? ST77XX_YELLOW : ok ? ST77XX_WHITE : COLOR_DARK_GREY, ST77XX_BLACK);
    x += textWidth(parts[i], 1);
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
  drawCentered("Меню", MENU_HEADER_Y, 2, ST77XX_CYAN);
  drawMenuList();
}

// «Сведения»: прошивка, заряд, сколько бросков, номер посоха, подтверждено ли время.
// Только чтение, удобно для замеров автономности.
void screenMenuInfo() {
  tft.fillScreen(ST77XX_BLACK);
  statusShowTime = true;
  drawStatusBar();
  drawCentered("Сведения", MENU_HEADER_Y, 2, ST77XX_CYAN);
  char line[64];   // русские буквы по два байта: запас вдвое
  int y = MENU_ROW_Y;
  snprintf(line, sizeof(line), "Прошивка %s", FW_VERSION);
  drawText(tft, 8, y, line, 2, ST77XX_WHITE, ST77XX_BLACK); y += MENU_ROW_H;
  snprintf(line, sizeof(line), "Заряд %d мВ %d%%", batteryMillivolts(), batteryPercent());
  drawText(tft, 8, y, line, 2, ST77XX_WHITE, ST77XX_BLACK); y += MENU_ROW_H;
  snprintf(line, sizeof(line), "Бросков %u", historyLastId());
  drawText(tft, 8, y, line, 2, ST77XX_WHITE, ST77XX_BLACK); y += MENU_ROW_H;
  snprintf(line, sizeof(line), "ID %u", staffId);
  drawText(tft, 8, y, line, 2, ST77XX_WHITE, ST77XX_BLACK); y += MENU_ROW_H;
  if (rtcSynced) drawText(tft, 8, y, "Время верное",    2, ST77XX_GREEN,  ST77XX_BLACK);
  else           drawText(tft, 8, y, "Время не задано", 2, ST77XX_ORANGE, ST77XX_BLACK);
  y += MENU_ROW_H;
  if (!rtcSynced) drawText(tft, 8, y, "время ставит планшет при связи", 1, COLOR_GREY, ST77XX_BLACK);
  drawCentered("ОК / Отмена назад", MENU_HINT_Y, 1, COLOR_GREY);
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
      char value[32];
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
