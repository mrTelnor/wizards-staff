#pragma once
#include <SPI.h>
#include <Adafruit_GFX.h>
#include <Adafruit_ST7789.h>
#include "config.h"
#include "dice.h"
#include "rtc.h"

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

// Запускает экран и включает подсветку. Вызывать один раз в setup().
// SPI поднимаем сами со своими ножками: если это сделает библиотека, она возьмёт
// ножки по умолчанию, а у C3 среди них GPIO5, где у нас подсветка.
void displayBegin() {
  pinMode(PIN_TFT_BLK, OUTPUT);
  SPI.begin(PIN_TFT_SCK, -1, PIN_TFT_MOSI, -1);
  tft.init(TFT_SIZE, TFT_SIZE, TFT_SPI_MODE);
  tft.setSPISpeed(TFT_SPI_HZ);
  tft.setRotation(TFT_ROTATION);
  tft.fillScreen(ST77XX_BLACK);
  analogWrite(PIN_TFT_BLK, 255);   // подсветка на полную; яркость появится в настройках
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
  int fill = constrain(statusBattPct, 0, 100) * 26 / 100;
  tft.fillRect(x + 2, y + 2, fill, 10, statusBattPct < 20 ? ST77XX_RED : ST77XX_GREEN);
}

// Значок Bluetooth слева от батареи, как в строке состояния телефона: руна из линий.
// Планшет подключён — голубой, нет — тёмно-серый, едва заметный.
void drawBleIcon() {
  const int cx = 188, top = STATUS_Y, bottom = STATUS_Y + 16;
  const int up = top + 4, down = bottom - 4;   // где боковые усики сходятся к стволу
  tft.fillRect(cx - 6, top, 13, 17, ST77XX_BLACK);
  uint16_t c = statusConnected ? COLOR_BLE : COLOR_DARK_GREY;
  tft.drawLine(cx, top, cx, bottom, c);             // ствол
  tft.drawLine(cx, top, cx + 5, up, c);             // верхний «клюв»
  tft.drawLine(cx + 5, up, cx - 5, down, c);        // косая сверху вниз
  tft.drawLine(cx, bottom, cx + 5, down, c);        // нижний «клюв»
  tft.drawLine(cx + 5, down, cx - 5, up, c);        // косая снизу вверх
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
void screenArmed(int count, int sides) {
  tft.fillScreen(ST77XX_BLACK);
  statusShowTime = true;
  drawStatusBar();
  char buf[16];
  snprintf(buf, sizeof(buf), "%dd%d", count, sides);
  drawCentered(buf, 80, 6, ST77XX_YELLOW);
  drawCentered("HIT THE FLOOR", 180, 2, ST77XX_WHITE);
}

// Размер шрифта суммы по числу знаков. Шрифт 10 даёт цифру 60 точек шириной:
// три цифры — 180 точек, четыре уже впритык к краям. Длиннее — мельче.
// Подбирается по самой большой возможной сумме броска, а не по выпавшей:
// иначе во время барабана цифры прыгали бы в размере.
uint8_t totalTextSize(long largest) {
  if (largest < 1000)  return 10;
  if (largest < 10000) return 8;
  return 6;
}

// Рисует число по центру полосы суммы в памяти, а не на экране. Сдвиг offset
// поднимает число вверх: так оно выезжает снизу и уходит за верхний край полосы.
void drawTotalIn(GFXcanvas16& canvas, long value, int offset, uint8_t size, uint16_t color) {
  char buf[8];
  snprintf(buf, sizeof(buf), "%ld", value);
  int16_t x1, y1;
  uint16_t w, h;
  canvas.setTextSize(size);
  canvas.setTextColor(color);
  canvas.getTextBounds(buf, 0, 0, &x1, &y1, &w, &h);
  canvas.setCursor((TFT_SIZE - w) / 2, (TOTAL_H - h) / 2 - offset);
  canvas.print(buf);
}

// Переносит полосу суммы из памяти на экран одним куском, без мигания.
void pushTotal(GFXcanvas16& canvas) {
  tft.drawRGBBitmap(0, TOTAL_Y, canvas.getBuffer(), TFT_SIZE, TOTAL_H);
}

// Смена числа в полосе: старое уезжает вверх, новое выезжает снизу, как в окошке
// игрового автомата. frames кадров на одну смену: на SPI в 20 МГц кадр полосы
// уходит на экран примерно за 18 мс, больше трёх кадров на быстром начале не успеть.
void slideTotal(GFXcanvas16& canvas, long from, long to, uint8_t size,
                uint16_t fromColor, uint16_t toColor, int frames) {
  for (int f = 1; f <= frames; f++) {
    int shift = TOTAL_H * f / frames;
    canvas.fillScreen(ST77XX_BLACK);
    drawTotalIn(canvas, from, shift, size, fromColor);
    drawTotalIn(canvas, to, shift - TOTAL_H, size, toColor);
    pushTotal(canvas);
  }
}

// Результат броска с анимацией, как в приложении на планшете: сверху что бросали,
// в полосе суммы крутится барабан из случайных сумм, сначала быстро, потом всё
// медленнее, и останавливается на выпавшей. Пока крутится, числа бледные, а
// слагаемые спрятаны за «...»: по ним был бы виден результат.
// Длится ROLL_ANIM_MS и всё это время главный цикл стоит: удары после броска
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
  uint8_t size  = totalTextSize(largest);

  uint16_t finalColor = ST77XX_WHITE;
  if (r.count == 1 && r.sides == 20 && r.total == 20) finalColor = ST77XX_YELLOW;
  if (r.count == 1 && r.sides == 20 && r.total == 1)  finalColor = ST77XX_RED;

  // Полоса суммы рисуется в памяти: 240×96 точек по 2 байта, около 45 КБ.
  // Памяти нет — просто показываем итог без барабана.
  GFXcanvas16 canvas(TFT_SIZE, TOTAL_H);
  bool animate = canvas.getBuffer() != nullptr;

  if (r.count > 1) drawCentered("...", BREAKDOWN_Y, 2, COLOR_GREY);

  long shown = smallest;
  if (animate) {
    canvas.fillScreen(ST77XX_BLACK);
    drawTotalIn(canvas, shown, 0, size, COLOR_GREY);
    pushTotal(canvas);

    unsigned long startedAt = millis();
    unsigned long step = 45;
    while (millis() - startedAt < ROLL_ANIM_MS) {
      unsigned long frameAt = millis();
      long next = shown;
      // Повтор того же числа выглядел бы как остановка барабана, поэтому
      // перебрасываем, если выпало прежнее (кроме броска, где сумма всегда одна).
      for (int tries = 0; next == shown && tries < 5; tries++) next = random(smallest, largest + 1);
      slideTotal(canvas, shown, next, size, COLOR_GREY, COLOR_GREY, 3);
      shown = next;
      while (millis() - frameAt < step) delay(1);
      step = min(step * 118 / 100, 220UL);   // барабан постепенно замедляется
    }
    // Остановка: выезжает настоящая сумма, уже в полном цвете, и замирает.
    slideTotal(canvas, shown, r.total, size, COLOR_GREY, finalColor, 4);
  } else {
    char total[8];
    snprintf(total, sizeof(total), "%d", r.total);
    tft.fillRect(0, TOTAL_Y, TFT_SIZE, TOTAL_H, ST77XX_BLACK);
    drawCentered(total, TOTAL_Y + (TOTAL_H - 8 * size) / 2, size, finalColor);
  }

  if (r.count > 1) {
    tft.fillRect(0, BREAKDOWN_Y, TFT_SIZE, 16, ST77XX_BLACK);   // убираем «...»
    rollBreakdown(r, buf, sizeof(buf));
    // Шрифт 2 вмещает 20 знаков в строку, шрифт 1 — 40. Слагаемые длиннее
    // сорока знаков (десять d1000) просто обрежутся у края.
    drawCentered(buf, BREAKDOWN_Y, strlen(buf) <= 20 ? 2 : 1, ST77XX_WHITE);
  }
}

// Обновляет время там, где оно сейчас на экране: крупно на часах или мелко в углу.
// Вызывается раз в минуту из главного цикла.
void displayTick(long epoch, bool clockScreen) {
  if (clockScreen) drawClockTime(epoch);
  else             drawStatusTime();
}
