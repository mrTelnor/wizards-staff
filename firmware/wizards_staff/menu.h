#pragma once
#include "config.h"
#include "settings.h"
#include "display.h"
#include "sound.h"
#include "battery.h"
#include "history.h"
#include "identity.h"
#include "rtc.h"
#include "help.h"

// Меню посоха на кнопке «ОК» (SPEC 2.5). Здесь экран меню и что делают в нём кнопки
// ОК, ◄, ►, Отмена. Сами значения и их хранение — в settings.h.
//
// Два уровня: список пунктов и правка одного пункта. В списке ◄ ► выбирают пункт,
// ОК открывает его, Отмена выходит из меню. В правке ◄ ► меняют значение, и оно
// сразу видно и слышно; ОК сохраняет во флеш, Отмена возвращает прежнее.
// Выход из меню по времени или любым другим путём (кость, удар, сон) тоже возвращает
// несохранённое: см. menuLeave.
//
// «Сведения» и «Инструкция» — пункты только для чтения: ОК открывает их экран, ОК или
// Отмена возвращают в список. В инструкции ◄ ► листают страницы из help.h без круга.

enum MenuItem { MENU_BRIGHT, MENU_VOLUME, MENU_CLOCK, MENU_SLEEP, MENU_TEST, MENU_INFO, MENU_HELP, MENU_COUNT };
const char* const MENU_NAMES[MENU_COUNT] = { "Яркость", "Звук", "К часам", "Сон", "Тест d20", "Сведения", "Инструкция" };

const int MENU_HEADER_Y = 30;
const int MENU_ROW_Y    = 56;    // верх первой строки
// Шаг строк. С семью пунктами 23: фон последней строки кончается на 211-й точке, до подсказки
// на 222 (с полосой над ней — 220) остаётся зазор. Текст кеглем 2 с запасом над строкой
// занимает 20 точек (правило в шапке text.h), так что 23 хватает.
const int MENU_ROW_H    = 23;
const int MENU_HINT_Y   = 222;   // нижняя строка мелко: подсказка или ступени таймаута
// Сколько пунктов видно разом. Влезает не больше 7 (см. MENU_ROW_H); пунктов больше —
// список прокручивается за курсором, а скрытые пункты выше и ниже показывают стрелки ▲ ▼.
const int MENU_VISIBLE  = 7;
// Стрелки прокрутки кеглем 2 у правого края. ▲ — в строке заголовка «Меню». ▼ — внизу
// справа, рядом с подсказкой: y 215 отстоит от текста последней строки (194) на 21 ≥ 20
// (правило text.h), а полоса над ней (211–214) начинается под фоном строки (до 210).
const int MENU_ARROW_X      = TFT_SIZE - 8 - 12;
const int MENU_ARROW_DOWN_Y = 215;
const int HELP_ROW_Y    = 52;    // инструкция: верх первой строки текста
const int HELP_ROW_H    = 20;    // и шаг строк: 10·size при кегле 2, ближе нельзя (шапка text.h)

bool          menuActive  = false;   // меню открыто (может быть, уже ушли с его экрана — см. menuLeave)
int           menuSel     = 0;       // выбранный пункт
int           menuTop     = 0;       // первый видимый пункт, когда пунктов больше MENU_VISIBLE
bool          menuEditing = false;   // правим значение выбранного пункта
bool          menuInfo    = false;   // открыт экран «Сведения»
bool          menuHelp    = false;   // открыта «Инструкция»
int           menuHelpPage = 0;      // какая страница инструкции на экране
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

// Одна строка списка: имя слева, значение справа. item должен быть в окне
// menuTop … menuTop + MENU_VISIBLE − 1, место строки на экране считается от menuTop.
// Выбранная на тёмно-синем фоне, значение в правке жёлтое в стрелках «< 80% >».
// Строка закрашивает свой фон сама, полоса 2·size над текстом (под Ё и Й) лежит
// внутри этого фона.
// Самая тесная строка — «К часам < 1,5 мин >»: имя до 92-й точки, значение со 102-й.
void drawMenuRow(int item) {
  int y = MENU_ROW_Y + (item - menuTop) * MENU_ROW_H;
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

// Сдвигает окно списка так, чтобы курсор был виден: ушёл выше — окно начинается с него,
// ниже — кончается на нём. Переход по кругу с первого на последний ставит окно в конец,
// с последнего на первый — в начало.
void menuScrollToSel() {
  if (menuSel < menuTop) menuTop = menuSel;
  if (menuSel >= menuTop + MENU_VISIBLE) menuTop = menuSel - MENU_VISIBLE + 1;
}

// Стрелки прокрутки: ▲, если выше окна есть пункты, ▼ — если ниже. Нет скрытых пунктов
// (сейчас, при 7 пунктах из 7) — на их месте чёрное. В правке значения стрелок тоже нет:
// листать список в ней нельзя, а подсказка правки занимает строку почти до края.
// Рисовать после drawMenuHint: та стирает свою строку целиком, вместе с низом ▼.
void drawMenuArrows() {
  bool up   = !menuEditing && menuTop > 0;
  bool down = !menuEditing && menuTop + MENU_VISIBLE < MENU_COUNT;
  if (up) drawText(tft, MENU_ARROW_X, MENU_HEADER_Y, "▲", 2, COLOR_GREY, ST77XX_BLACK);
  else    tft.fillRect(MENU_ARROW_X, MENU_HEADER_Y - 4, 12, 20, ST77XX_BLACK);
  if (down) drawText(tft, MENU_ARROW_X, MENU_ARROW_DOWN_Y, "▼", 2, COLOR_GREY, ST77XX_BLACK);
  else      tft.fillRect(MENU_ARROW_X, MENU_ARROW_DOWN_Y - 4, 12, MENU_HINT_Y - 2 - (MENU_ARROW_DOWN_Y - 4), ST77XX_BLACK);   // верх ▼ над строкой подсказки; низ стёрла drawMenuHint
}

// Видимые строки, нижняя строка и стрелки, без стирания экрана: для каждого шага ◄ ►.
// Строки окна перерисовываются целиком, с фоном на всю ширину, поэтому при прокрутке
// от прежних пунктов хвостов не остаётся.
void drawMenuList() {
  for (int i = menuTop; i < menuTop + MENU_VISIBLE && i < MENU_COUNT; i++) drawMenuRow(i);
  drawMenuHint();
  drawMenuArrows();
}

// Экран меню целиком: при входе и при возврате со «Сведений» и «Инструкции».
// Окно прокрутки (menuTop) при возврате прежнее, курсор на том же пункте.
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

// Страница инструкции menuHelpPage: заголовок «Кости 1/8» и строки текста.
// Экран не стирает: каждая строка закрашивает фон под собой и до правого края, а строки,
// которых на этой странице нет, закрашиваются чёрным. Поэтому листание идёт без мигания.
void drawHelpPage() {
  const HelpPage& page = HELP_PAGES[menuHelpPage];
  char title[48];   // русские буквы по два байта: запас вдвое
  snprintf(title, sizeof(title), "%s %d/%d", page.title, menuHelpPage + 1, HELP_PAGE_COUNT);
  drawCenteredLine(title, MENU_HEADER_Y, 2, ST77XX_CYAN);   // заголовки разной длины, без мигания
  for (int i = 0; i < HELP_LINES; i++) {
    int y = HELP_ROW_Y + i * HELP_ROW_H;
    const char* text = page.lines[i];
    int right = 0;   // где кончился текст строки
    if (text) {
      drawText(tft, 8, y, text, 2, ST77XX_WHITE, ST77XX_BLACK);
      right = 8 + textWidth(text, 2);
    }
    tft.fillRect(right, y - 4, TFT_SIZE - right, HELP_ROW_H, ST77XX_BLACK);   // хвост прежней строки
  }
}

// «Инструкция» целиком: при входе в пункт.
void screenMenuHelp() {
  tft.fillScreen(ST77XX_BLACK);
  statusShowTime = true;
  drawStatusBar();
  drawHelpPage();
  drawCentered("◄ ► листать   ОК/Отмена назад", MENU_HINT_Y, 1, COLOR_GREY);
}

// «ОК» вне меню: открыть его на первом пункте.
void menuOpen() {
  menuActive  = true;
  menuSel     = 0;
  menuTop     = 0;
  menuEditing = false;
  menuInfo    = false;
  menuHelp    = false;
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
  menuHelp    = false;
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

  if (menuHelp) {                                   // «Инструкция»: ◄ ► листают, ОК и Отмена — в список
    if (button == BTN_PREV && menuHelpPage > 0)                   { menuHelpPage--; drawHelpPage(); }
    if (button == BTN_NEXT && menuHelpPage < HELP_PAGE_COUNT - 1) { menuHelpPage++; drawHelpPage(); }
    if (button == BTN_OK || button == BTN_CANCEL) {
      menuHelp = false;
      screenMenu();                                 // курсор остаётся на «Инструкции»
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

  // В списке ◄ ► ходят по кругу: с первого пункта на последний и обратно.
  if (button == BTN_PREV) { menuSel = (menuSel + MENU_COUNT - 1) % MENU_COUNT; menuScrollToSel(); drawMenuList(); }
  if (button == BTN_NEXT) { menuSel = (menuSel + 1) % MENU_COUNT;              menuScrollToSel(); drawMenuList(); }
  if (button == BTN_OK) {
    if (menuSel == MENU_INFO) {
      menuInfo = true;
      screenMenuInfo();
    } else if (menuSel == MENU_HELP) {
      menuHelp     = true;
      menuHelpPage = 0;
      screenMenuHelp();
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
