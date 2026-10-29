#pragma once
#include <SPI.h>
#include <Adafruit_GFX.h>
#include <Adafruit_ST7789.h>
#include "config.h"
#include "dice.h"
#include "history.h"
#include "rtc.h"
#include "log.h"
#include "sound.h"
#include "lights.h"

// Экран 1,54" ST7789, 240×240 точек. Здесь только рисование: что и когда показывать,
// решает главный цикл. Шрифт встроенный, без кириллицы, поэтому всё на экране латиницей.

// Режим SPI и поворот подбираются под конкретный модуль. Наш модуль без ножки CS
// откликается только в режиме 3 (подобрано 2026-10-22 скетчем tools/display_test,
// в режимах 0–2 экран светится ровным серым и не рисует ничего). Другой модуль
// не отвечает — прогнать tools/display_test. Картинка вверх ногами — поворот 2.
const uint8_t  TFT_SPI_MODE = SPI_MODE3;
const uint8_t  TFT_ROTATION = 0;
// Частота шины. Провода Dupont по 20 см на макетке 40 МГц по умолчанию могут
// не держать, отсюда с запасом 20 МГц. После пайки начисто можно поднять.
const uint32_t TFT_SPI_HZ   = 20000000;

const int TFT_SIZE = 240;   // экран квадратный

// Разметка экрана по высоте, в точках от верхнего края.
const int STATUS_Y    = 4;     // строка состояния: время слева, связь и батарея справа
const int HEADER_Y    = 34;    // «3d6» над результатом
const int TOTAL_Y     = 66;    // полоса с суммой: в ней же крутится барабан
const int TOTAL_H     = 96;    // её высота: цифра шрифта 10 высотой 80 и поля
const int BREAKDOWN_Y = 192;   // слагаемые

// Цвета, которых нет готовыми в библиотеке. Формат RGB565: 5 бит красного,
// 6 зелёного, 5 синего.
const uint16_t COLOR_GREY      = 0x7BEF;   // бледные числа, пока крутится барабан
const uint16_t COLOR_DARK_GREY = 0x4208;   // значок связи, когда планшета нет
const uint16_t COLOR_BLE       = 0x3D7F;   // значок связи, когда планшет подключён

// CS у модуля на земле: устройство на шине одно, выбирать некого. Поэтому первым аргументом -1.
Adafruit_ST7789 tft(-1, PIN_TFT_DC, PIN_TFT_RST);

// Что показывает строка состояния. Хранится здесь, а не передаётся в каждый экран:
// меняется в своё время (заряд раз в 10 с, связь при подключении), а нужна всем экранам.
int  statusBattPct   = 0;
bool statusConnected = false;
bool statusShowTime  = false;   // на экране часов мелкое время в углу не нужно

uint8_t displayBacklight = 255;  // яркость подсветки из меню, ставит её settingsApply()
bool    displayTestMark  = false; // тестовый режим d20 включён: на взводе 1d20 пометка TEST

// Запускает экран и включает подсветку. Вызывать один раз в setup().
// SPI поднимаем сами со своими ножками: если это сделает библиотека, она возьмёт
// ножки по умолчанию, а у C3 среди них GPIO5, где у нас подсветка.
void displayBegin() {
  pinMode(PIN_TFT_BLK, OUTPUT);
  SPI.begin(PIN_TFT_SCK, -1, PIN_TFT_MOSI, -1);
  tft.init(TFT_SIZE, TFT_SIZE, TFT_SPI_MODE);
  tft.setSPISpeed(TFT_SPI_HZ);
  tft.setRotation(TFT_ROTATION);
  tft.setTextWrap(false);   // см. drawNumberIn: перенос строк прятал однозначные суммы
  tft.fillScreen(ST77XX_BLACK);
  analogWrite(PIN_TFT_BLK, displayBacklight);
}

// Яркость подсветки из меню, ШИМ от 0 до 255 (BRIGHT_LEVELS в config.h). Действует сразу.
void displaySetBrightness(uint8_t pwm) {
  displayBacklight = pwm;
  analogWrite(PIN_TFT_BLK, pwm);
}

// Гасит экран: подсветку и саму матрицу (команда SLPIN, матрица почти не потребляет).
// Картинка в памяти экрана сохраняется, но после пробуждения её всё равно перерисуют.
void displaySleep() {
  analogWrite(PIN_TFT_BLK, 0);
  tft.enableSleep(true);
}

// Будит экран. После SLPOUT матрице нужно 5 мс до следующей команды (паспорт ST7789),
// берём с запасом.
void displayWake() {
  tft.enableSleep(false);
  delay(10);
  analogWrite(PIN_TFT_BLK, displayBacklight);
}

// Печатает строку по центру экрана по горизонтали, верх строки на высоте y.
// size — увеличение встроенного шрифта: 1 это 6×8 точек на букву, 2 это 12×16 и так далее.
// Фон закрашивается чёрным под каждой буквой, поэтому строку той же длины можно
// перерисовать поверх старой без мигания всего экрана.
void drawCentered(const char* text, int y, uint8_t size, uint16_t color) {
  int16_t x1, y1;
  uint16_t w, h;
  tft.setTextSize(size);
  tft.setTextColor(color, ST77XX_BLACK);
  tft.getTextBounds(text, 0, y, &x1, &y1, &w, &h);
  tft.setCursor((TFT_SIZE - w) / 2, y);
  tft.print(text);
}

// ===== Строка состояния =====

// Значок батареи в правом верхнем углу: рамка и заливка по проценту заряда,
// красная ниже 20 %. Рисуется поверх старого, экран целиком не стирает.
void drawBattery() {
  const int x = 203, y = STATUS_Y + 1;
  tft.drawRect(x, y, 30, 14, ST77XX_WHITE);
  tft.fillRect(x + 30, y + 4, 3, 6, ST77XX_WHITE);    // «пипка» батарейки
  tft.fillRect(x + 2, y + 2, 26, 10, ST77XX_BLACK);   // стираем прежнюю заливку
  int pct = constrain(statusBattPct, 0, 100);
  tft.fillRect(x + 2, y + 2, pct * 26 / 100, 10, pct < 20 ? ST77XX_RED : ST77XX_GREEN);

  // Слева от значка заряд числом, как на телефоне: «77%». Прижат вправо к значку,
  // поэтому сначала стираем место под самое длинное «100%», потом пишем.
  char text[6];
  snprintf(text, sizeof(text), "%d%%", pct);
  const int right = x - 4;                               // правый край видимых букв
  const int widest = (6 * 4 - 1) * 2;                    // «100%» шрифтом 2
  tft.fillRect(right - widest, STATUS_Y, widest + 1, 17, ST77XX_BLACK);
  tft.setTextSize(2);
  tft.setTextColor(ST77XX_WHITE, ST77XX_BLACK);
  tft.setCursor(right - ((6 * (int)strlen(text) - 1) * 2 - 1), STATUS_Y + 1);
  tft.print(text);
}

// Значок Bluetooth слева от заряда в процентах, как в строке состояния телефона: руна из линий.
// Планшет подключён — голубой, нет — тёмно-серый, едва заметный.
void drawBleIcon() {
  const int cx = 143, top = STATUS_Y, bottom = STATUS_Y + 16;
  const int up = top + 4, down = bottom - 4;   // где боковые усики сходятся к стволу
  tft.fillRect(cx - 6, top, 13, 17, ST77XX_BLACK);
  uint16_t c = statusConnected ? COLOR_BLE : COLOR_DARK_GREY;
  tft.drawLine(cx, top, cx, bottom, c);             // ствол
  tft.drawLine(cx, top, cx + 5, up, c);             // верхний «клюв»
  tft.drawLine(cx + 5, up, cx - 5, down, c);        // косая сверху вниз
  tft.drawLine(cx, bottom, cx + 5, down, c);        // нижний «клюв»
  tft.drawLine(cx + 5, down, cx - 5, up, c);        // косая снизу вверх
}

// Перечёркнутый динамик слева от значка связи, пока звук выключен кнопкой «Тихо»:
// видно, почему посох молчит. Звук включён — место пустое.
void drawMuteIcon() {
  const int x = 110, y = STATUS_Y;
  tft.fillRect(x, y, 21, 17, ST77XX_BLACK);
  if (soundEnabled) return;
  tft.fillRect(x + 1, y + 5, 4, 7, ST77XX_WHITE);                           // корпус динамика
  tft.fillTriangle(x + 4, y + 8, x + 10, y + 2, x + 10, y + 14, ST77XX_WHITE); // раструб
  tft.drawLine(x + 13, y + 5, x + 19, y + 11, ST77XX_RED);                 // крест
  tft.drawLine(x + 13, y + 11, x + 19, y + 5, ST77XX_RED);
}

// Мелкое время в левом верхнем углу. На экране часов не рисуется: там оно и так крупно.
// Строка всегда «ЧЧ:ММ» одной длины, поэтому ложится поверх старой.
void drawStatusTime() {
  if (!statusShowTime) return;
  long epoch = rtcEpoch();
  char full[24];
  rtcFormat(epoch, full, sizeof(full));   // «2026-10-22 21:45:03» в местном времени
  char hm[6] = "--:--";
  if (epoch) snprintf(hm, sizeof(hm), "%.5s", full + 11);
  tft.setTextSize(2);
  tft.setTextColor(ST77XX_WHITE, ST77XX_BLACK);
  tft.setCursor(6, STATUS_Y + 1);
  tft.print(hm);
}

// Вся строка состояния разом: при смене экрана.
void drawStatusBar() {
  drawStatusTime();
  drawMuteIcon();
  drawBleIcon();
  drawBattery();
}

// Новый замер заряда: запомнить и перерисовать значок.
void displaySetBattery(int pct) {
  statusBattPct = pct;
  drawBattery();
}

// Планшет подключился или отключился: перерисовать значок связи.
void displaySetConnected(bool connected) {
  statusConnected = connected;
  drawBleIcon();
}

// ===== Экраны =====

// Время и дату на экране покоя. Отдельно от screenClock, чтобы раз в минуту
// обновлять только их: строки всегда одной длины и ложатся поверх старых.
// Если часы не выставлены (epoch равен нулю), вместо времени прочерки.
void drawClockTime(long epoch) {
  char full[24];
  rtcFormat(epoch, full, sizeof(full));
  char hm[6] = "--:--";
  char dm[6] = "--.--";
  if (epoch) {
    snprintf(hm, sizeof(hm), "%.5s", full + 11);                // «21:45»
    snprintf(dm, sizeof(dm), "%.2s.%.2s", full + 8, full + 5);  // «22.10»
  }
  drawCentered(hm, 80, 6, ST77XX_CYAN);
  drawCentered(dm, 150, 3, ST77XX_WHITE);
}

// Экран покоя: время крупно, под ним дата, в строке состояния связь и батарея.
void screenClock(long epoch) {
  tft.fillScreen(ST77XX_BLACK);
  statusShowTime = false;
  drawStatusBar();
  drawClockTime(epoch);
}

// Посох взведён: что бросаем, например «3d6», и подсказка, что делать дальше.
// С удачей под «1d20» зелёное FORTUNE, с неудачей красное MISFORTUNE (SPEC 2.5).
void screenArmed(int count, int sides, uint8_t flags) {
  tft.fillScreen(ST77XX_BLACK);
  statusShowTime = true;
  drawStatusBar();
  char buf[16];
  snprintf(buf, sizeof(buf), "%dd%d", count, sides);
  drawCentered(buf, 80, 6, ST77XX_YELLOW);
  if (flags == ROLL_FORTUNE)    drawCentered("FORTUNE", 140, 3, ST77XX_GREEN);
  if (flags == ROLL_MISFORTUNE) drawCentered("MISFORTUNE", 140, 3, ST77XX_RED);
  // Тестовый режим подменяет 20 и 1: видно на каждом взводе, чтобы не забыть выключить перед игрой.
  if (displayTestMark && count == 1 && sides == 20) drawCentered("TEST", 210, 2, ST77XX_ORANGE);
  drawCentered("HIT THE FLOOR", 180, 2, ST77XX_WHITE);
}

// Встроенный шрифт: цифра 5×7 точек в клетке 6×8, шестая колонка и восьмая строка
// пустые — это промежутки. При увеличении size всё умножается на size.
// Видимая ширина n цифр: (6n − 1) × size, без пустой колонки за последней.
// Видимая высота цифры: 7 × size, у цифр нет хвостов вниз.
int totalInkWidth(int digits, uint8_t size) { return (6 * digits - 1) * size; }
int totalInkHeight(uint8_t size)            { return 7 * size; }

// Сколько цифр в числе.
int digitsOf(long value) {
  int digits = 1;
  for (long v = value; v >= 10; v /= 10) digits++;
  return digits;
}

// Размер шрифта суммы по числу знаков. Шрифт 10 даёт цифру 60 точек шириной:
// три цифры — 180 точек, четыре уже впритык к краям. Длиннее — мельче.
// Подбирается по самой большой возможной сумме броска, а не по выпавшей:
// иначе во время барабана цифры прыгали бы в размере.
// Пробовали 2026-10-23 шрифт впритык во всю высоту экрана (до 22): автору не
// понравилось, вернулись к этим размерам.
uint8_t totalTextSize(long largest) {
  if (largest < 1000)  return 10;
  if (largest < 10000) return 8;
  return 6;
}

// На сколько ступеней шрифта сумма вырастает во вспышке после остановки барабана.
const uint8_t       FLASH_GROW = 2;
const unsigned long FLASH_MS   = 160;   // сколько держится вспышка

// Окно барабана: прямоугольник ровно под самую широкую возможную сумму броска
// в размере вспышки, по центру полосы суммы. Числа крутятся и вспыхивают только
// внутри него, и на экран каждый кадр уходит только это окно, а не полоса во всю
// ширину: для 3d6 это 136×88 точек вместо 240×96, вдвое меньше данных.
struct RollWindow {
  int x, y, w, h;        // где окно на экране и его размер
  uint8_t size;          // шрифт барабана и итога
};

// Считает окно барабана под бросок с наибольшей возможной суммой largest.
RollWindow rollWindow(long largest) {
  RollWindow win;
  win.size = totalTextSize(largest);
  uint8_t big = win.size + FLASH_GROW;
  win.w = min(totalInkWidth(digitsOf(largest), big) + 4, TFT_SIZE);
  win.h = min(totalInkHeight(big) + 4, TOTAL_H);
  win.x = (TFT_SIZE - win.w) / 2;
  win.y = TOTAL_Y + (TOTAL_H - win.h) / 2;
  return win;
}

// Рисует число по центру окна в памяти, а не на экране. Центруем по видимым точкам
// сами: getTextBounds библиотеки считает и пустую колонку за последней цифрой,
// и крупное число уезжало бы влево. Сдвиг offset поднимает число вверх: так оно
// выезжает снизу и уходит за верхний край окна.
void drawNumberIn(GFXcanvas16& canvas, const RollWindow& win, long value, int offset,
                  uint8_t size, uint16_t color) {
  // Перенос строк выключен обязательно. Библиотека переносит букву на следующую
  // строку, если та «не влезает» вместе с пустой колонкой справа. Окно однозначной
  // суммы узкое (64 точки), цифра со столбцом — 60 от отступа 7, и цифра уезжала
  // за нижний край окна: на экране было пусто. Пойман 2026-10-24 на 1d6 и 2d4.
  canvas.setTextWrap(false);
  canvas.setTextSize(size);
  canvas.setTextColor(color);
  canvas.setCursor((win.w - totalInkWidth(digitsOf(value), size)) / 2,
                   (win.h - totalInkHeight(size)) / 2 - offset);
  canvas.print(value);
}

// Переносит окно из памяти на экран одним куском, без мигания.
void pushWindow(GFXcanvas16& canvas, const RollWindow& win) {
  tft.drawRGBBitmap(win.x, win.y, canvas.getBuffer(), win.w, win.h);
}

// Одно число в окне без движения: начальный кадр, вспышка и итог.
void showNumber(GFXcanvas16& canvas, const RollWindow& win, long value,
                uint8_t size, uint16_t color) {
  canvas.fillScreen(ST77XX_BLACK);
  drawNumberIn(canvas, win, value, 0, size, color);
  pushWindow(canvas, win);
}

// Смена числа: старое уезжает вверх, новое выезжает снизу, как в окошке игрового
// автомата. Оба числа одного размера win.size. frames кадров на смену: на быстром
// начале барабана больше двух не успеть.
void slideNumber(GFXcanvas16& canvas, const RollWindow& win, long from, long to,
                 uint16_t fromColor, uint16_t toColor, int frames) {
  for (int f = 1; f <= frames; f++) {
    int shift = win.h * f / frames;
    canvas.fillScreen(ST77XX_BLACK);
    drawNumberIn(canvas, win, from, shift, win.size, fromColor);
    drawNumberIn(canvas, win, to, shift - win.h, win.size, toColor);
    pushWindow(canvas, win);
  }
}

// Результат броска с анимацией, как в приложении на планшете: сверху что бросали,
// в окне крутится барабан из случайных сумм, сначала быстро, потом всё медленнее,
// и останавливается на выпавшей. Пока крутится, числа бледные, а слагаемые спрятаны
// за «...»: по ним был бы виден результат. В конце сумма на миг вспыхивает крупнее
// и встаёт на место.
// Длится около ROLL_ANIM_MS и всё это время главный цикл стоит: удары после броска
// всё равно не слушаются (STRIKE_COOLDOWN_MS), а планшету бросок уже отправлен.
// Для одного d20 двадцатка в итоге золотая, единица красная.
void screenResult(const Roll& r) {
  tft.fillScreen(ST77XX_BLACK);
  statusShowTime = true;
  drawStatusBar();

  char buf[64];
  snprintf(buf, sizeof(buf), "%dd%d", r.count, r.sides);
  drawCentered(buf, HEADER_Y, 3, ST77XX_CYAN);

  long smallest = r.count;                      // на всех костях единицы
  long largest  = (long)r.count * r.sides;      // на всех максимум
  RollWindow win = rollWindow(largest);

  // Цвет итога на экране и вспышка кольца в момент, когда сумма вырастает.
  uint16_t  finalColor = ST77XX_WHITE;
  RingFlash flash      = RING_FLASH_WHITE;
  if (r.count == 1 && r.sides == 20 && r.total == 20) { finalColor = ST77XX_YELLOW; flash = RING_FLASH_CRIT; }
  if (r.count == 1 && r.sides == 20 && r.total == 1)  { finalColor = ST77XX_RED;    flash = RING_FLASH_FAIL; }

  // Слагаемые, а у удачи и неудачи оба броска, прячутся за «...», пока крутится барабан.
  bool breakdown = r.count > 1 || r.flags;
  if (breakdown) drawCentered("...", BREAKDOWN_Y, 2, COLOR_GREY);

  // Окно рисуется в памяти: не больше 240×88 точек по 2 байта, до 42 КБ.
  // Памяти нет — просто показываем итог без барабана.
  GFXcanvas16 canvas(win.w, win.h);
  if (canvas.getBuffer()) {
    long shown = smallest;
    showNumber(canvas, win, shown, win.size, COLOR_GREY);

    unsigned long startedAt = millis();
    unsigned long step = 45;
    while (millis() - startedAt < ROLL_ANIM_MS) {
      unsigned long frameAt = millis();
      long next = shown;
      // Повтор того же числа выглядел бы как остановка барабана, поэтому
      // перебрасываем, если выпало прежнее.
      for (int tries = 0; next == shown && tries < 5; tries++) next = random(smallest, largest + 1);
      soundTick();   // тик на каждой смене числа, замедляется вместе с барабаном
      slideNumber(canvas, win, shown, next, COLOR_GREY, COLOR_GREY, 2);
      shown = next;
      while (millis() - frameAt < step) delay(1);
      step = min(step * 118 / 100, 220UL);   // барабан постепенно замедляется
    }
    // Остановка: выезжает настоящая сумма, уже в полном цвете,
    // вспыхивает крупнее и встаёт на место.
    slideNumber(canvas, win, shown, r.total, COLOR_GREY, finalColor, 3);
    showNumber(canvas, win, r.total, win.size + FLASH_GROW, finalColor);
    ringFlash(flash);   // кольцо вспыхивает вместе с ростом суммы
    ringWait(FLASH_MS);
    showNumber(canvas, win, r.total, win.size, finalColor);
  } else {
    logLine(LOG_ROLL, LOG_WARN, "нет %u байт памяти под барабан, итог без анимации",
            (unsigned)(win.w * win.h * 2));
    tft.setTextSize(win.size);
    tft.setTextColor(finalColor);
    tft.setCursor(win.x + (win.w - totalInkWidth(digitsOf(r.total), win.size)) / 2,
                  win.y + (win.h - totalInkHeight(win.size)) / 2);
    tft.print(r.total);
    ringFlash(flash);
  }

  if (breakdown) {
    tft.fillRect(0, BREAKDOWN_Y, TFT_SIZE, 16, ST77XX_BLACK);   // убираем «...»
    rollBreakdown(r, buf, sizeof(buf));
    // Шрифт 2 вмещает 20 знаков в строку, шрифт 1 — 40. Слагаемые длиннее
    // сорока знаков (десять d1000) просто обрежутся у края.
    // Оба броска удачи зелёные, неудачи красные: тем же цветом, что надпись при взводе.
    uint16_t color = r.flags == ROLL_FORTUNE ? ST77XX_GREEN
                   : r.flags == ROLL_MISFORTUNE ? ST77XX_RED : ST77XX_WHITE;
    drawCentered(buf, BREAKDOWN_Y, strlen(buf) <= 20 ? 2 : 1, color);
  }
}

// ===== История бросков (кнопки ◄ ►) =====

const int HIST_HEADER_Y = 30;    // «HISTORY 1-4/20»
const int HIST_ROW_Y    = 56;    // верх первой строки
const int HIST_ROW_H    = 46;    // строка: бросок шрифтом 2 и под ним слагаемые шрифтом 1
const int HIST_DICE_X   = 78;    // столбец «3d6», левее время броска
const int HIST_BG_TOP   = 5;     // фон строки начинается чуть выше текста...
const int HIST_BG_H     = 38;    // ...и захватывает слагаемые; между строками остаётся чёрный зазор

// Фон выбранной строки: тёмно-синий (24, 32, 72). Заметен издалека, но не слепит,
// и все цвета текста на нём читаются, включая серый.
const uint16_t COLOR_SELECT = 0x1909;

// Одна строка истории: время броска, что бросали, итог справа, под ними мелко слагаемые.
// Цвета как на экране результата: 20 на d20 золотом, 1 красным, удача зелёным, неудача красным.
// Строка сама закрашивает свой фон, поэтому перерисовывается поверх старой без мигания.
void drawHistoryRow(const HistoryRecord& rec, int y, bool selected) {
  uint16_t bg = selected ? COLOR_SELECT : ST77XX_BLACK;
  tft.fillRect(0, y - HIST_BG_TOP, TFT_SIZE, HIST_BG_H, bg);
  Roll r = historyToRoll(rec);
  char buf[64];

  char full[24];
  rtcFormat(rec.ts, full, sizeof(full));
  char hm[6] = "--:--";
  if (rec.ts) snprintf(hm, sizeof(hm), "%.5s", full + 11);
  tft.setTextSize(2);
  tft.setTextColor(COLOR_GREY, bg);
  tft.setCursor(6, y);
  tft.print(hm);

  snprintf(buf, sizeof(buf), "%dd%d", r.count, r.sides);
  tft.setTextColor(ST77XX_CYAN, bg);
  tft.setCursor(HIST_DICE_X, y);
  tft.print(buf);
  if (r.flags) {   // F — удача (FORTUNE), M — неудача (MISFORTUNE)
    tft.setTextColor(r.flags == ROLL_FORTUNE ? ST77XX_GREEN : ST77XX_RED, bg);
    tft.print(r.flags == ROLL_FORTUNE ? " F" : " M");
  }

  uint16_t color = ST77XX_WHITE;
  if (r.count == 1 && r.sides == 20 && r.total == 20) color = ST77XX_YELLOW;
  if (r.count == 1 && r.sides == 20 && r.total == 1)  color = ST77XX_RED;
  snprintf(buf, sizeof(buf), "%d", r.total);
  tft.setTextColor(color, bg);
  tft.setCursor(TFT_SIZE - 6 - (12 * (int)strlen(buf) - 2), y);   // итог прижат вправо
  tft.print(buf);

  if (r.count > 1 || r.flags) {
    rollBreakdown(r, buf, sizeof(buf));
    tft.setTextSize(1);
    tft.setTextColor(COLOR_GREY, bg);
    tft.setCursor(HIST_DICE_X, y + 20);
    tft.print(buf);   // длинные слагаемые (десять d1000) просто обрежутся у края
  }
}

// Заголовок и видимые строки: с броска top, выбран бросок sel. Экран не стирает,
// каждая строка закрашивает себя сама, поэтому годится и для каждого шага ◄ ►.
void drawHistoryList(const HistoryRecord* recs, int count, int top, int sel) {
  char buf[24];
  snprintf(buf, sizeof(buf), "HISTORY %d/%d", sel + 1, count);
  tft.fillRect(0, HIST_HEADER_Y, TFT_SIZE, 16, ST77XX_BLACK);   // «12/20» длиннее «9/20»
  drawCentered(buf, HIST_HEADER_Y, 2, ST77XX_CYAN);
  int last = min(top + HISTORY_PAGE, count);
  for (int i = top; i < last; i++) {
    drawHistoryRow(recs[i], HIST_ROW_Y + (i - top) * HIST_ROW_H, i == sel);
  }
}

// Экран истории целиком, при входе в неё. Свежие броски сверху.
void screenHistory(const HistoryRecord* recs, int count, int top, int sel) {
  tft.fillScreen(ST77XX_BLACK);
  statusShowTime = true;
  drawStatusBar();
  if (count == 0) {
    drawCentered("NO ROLLS YET", 110, 2, COLOR_GREY);
    return;
  }
  drawHistoryList(recs, count, top, sel);
}

// Обновляет время там, где оно сейчас на экране: крупно на часах или мелко в углу.
// Вызывается раз в минуту из главного цикла.
void displayTick(long epoch, bool clockScreen) {
  if (clockScreen) drawClockTime(epoch);
  else             drawStatusTime();
}
