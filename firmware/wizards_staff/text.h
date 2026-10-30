#pragma once
#include <Arduino.h>
#include <Adafruit_GFX.h>
#include "font_ru.h"

// Печать текста UTF-8 на экран или канву: латиница, цифры и знаки — встроенным шрифтом
// библиотеки, русские буквы — своей таблицей из font_ru.h. Все функции принимают
// Adafruit_GFX&, поэтому годятся и для tft, и для GFXcanvas16.
//
// Каждый знак занимает клетку 6×8 точек при size 1 (5 столбцов буквы и столбец просвета),
// 12×16 при size 2 и так далее, как у встроенного шрифта. Поэтому ширина строки считается
// по числу знаков, а не байт: русская буква в UTF-8 это два байта (G67).
//
// Высота строки. Заглавные Ё и Й полной высоты, а их точки и кратка стоят над клеткой,
// в двух строках шрифта над y. Поэтому строка, напечатанная с верхом в y, занимает
// по высоте от y − 2·size до y + 8·size:
//   - над y нужен свободный запас 2·size точек (у верхнего края экрана y ≥ 2·size);
//   - соседние строки ставить не ближе 10·size друг от друга (от y до y);
//   - с фоном (bg != color) drawText закрашивает фоном и эту полосу над каждой клеткой,
//     чтобы после «Ё» на месте «Е» не оставались точки.
// Ширина от этого не меняется.
//
// Что печатается вместо чего:
//   ◄ и ►            — треугольники встроенного шрифта (коды 0x11 и 0x10);
//   ▲ и ▼            — треугольники встроенного шрифта (коды 0x1E и 0x1F);
//   — и –            — обычный дефис «-»;
//   всё остальное, чего нет в шрифтах, и испорченные последовательности байт — «?».
//
// Строку можно передать nullptr: все функции считают её пустой (проверка в utf8Next).

// Знак, который рисуем вместо неизвестного.
const uint32_t TEXT_UNKNOWN = '?';

// Достаёт из строки очередной знак UTF-8, возвращает его номер в Юникоде и сдвигает p
// за него. На конце строки возвращает 0 и p не двигает. Зачем своя: строки в прошивке
// и с планшета приходят в UTF-8, а библиотека умеет печатать только по байту.
// Испорченная последовательность (оборвалась на середине, лишний байт продолжения)
// даёт TEXT_UNKNOWN; оборванную разбираем до первого чужого байта, чтобы он, в том числе
// завершающий ноль, остался следующему знаку. «Слишком длинная» запись (знак записан
// большим числом байт, чем нужно, например C0 80 вместо нуля) тоже даёт TEXT_UNKNOWN:
// иначе такой спрятанный ноль оборвал бы строку на середине.
// p == nullptr считается пустой строкой: возвращаем 0.
uint32_t utf8Next(const char*& p) {
  if (p == nullptr) return 0;
  uint8_t b = (uint8_t)*p;
  if (b == 0) return 0;
  p++;
  if (b < 0x80) return b;                // ASCII, один байт

  int more;                              // сколько байт продолжения ждём
  uint32_t code;
  if      ((b & 0xE0) == 0xC0) { more = 1; code = b & 0x1F; }
  else if ((b & 0xF0) == 0xE0) { more = 2; code = b & 0x0F; }
  else if ((b & 0xF8) == 0xF0) { more = 3; code = b & 0x07; }
  else return TEXT_UNKNOWN;              // байт продолжения без начала или мусор

  for (int i = 0; i < more; i++) {
    uint8_t c = (uint8_t)*p;
    if ((c & 0xC0) != 0x80) return TEXT_UNKNOWN;   // оборвалась: чужой байт не съедаем
    code = (code << 6) | (c & 0x3F);
    p++;
  }
  // Наименьший номер, которому нужно столько байт: 0x80 для двух, 0x800 для трёх,
  // 0x10000 для четырёх. Меньше — слишком длинная запись.
  const uint32_t minCode[4] = { 0, 0x80, 0x800, 0x10000 };
  if (code < minCode[more]) return TEXT_UNKNOWN;
  return code;
}

// Номер буквы в таблице FONT_RU или -1, если это не русская буква.
// Порядок таблицы описан в font_ru.h: А–Я, а–я, Ё, ё.
int textRuIndex(uint32_t code) {
  if (code >= 0x0410 && code <= 0x042F) return code - 0x0410;        // А–Я
  if (code >= 0x0430 && code <= 0x044F) return code - 0x0430 + 32;   // а–я
  if (code == 0x0401) return 64;                                      // Ё
  if (code == 0x0451) return 65;                                      // ё
  return -1;
}

// Номер надстрочного знака в FONT_RU_MARKS для буквы с номером index, или -1, если знака
// у неё нет. Знаки есть только у заглавных Ё и Й, поэтому простой перебор.
int textRuMark(int index) {
  for (int m = 0; m < FONT_RU_MARK_COUNT; m++) {
    if (pgm_read_byte(&FONT_RU_MARK_INDEX[m]) == index) return m;
  }
  return -1;
}

// Код встроенного шрифта для знака, который не русская буква. Неизвестное — «?».
uint8_t textBuiltinCode(uint32_t code) {
  if (code >= 0x20 && code <= 0x7E) return (uint8_t)code;   // ASCII: буквы, цифры, знаки
  if (code == 0x25C4) return 0x11;                          // ◄
  if (code == 0x25BA) return 0x10;                          // ►
  if (code == 0x25B2) return 0x1E;                          // ▲
  if (code == 0x25BC) return 0x1F;                          // ▼
  if (code == 0x2014 || code == 0x2013) return '-';         // — и –
  return TEXT_UNKNOWN;
}

// Рисует одну русскую букву из таблицы FONT_RU, левый верхний угол клетки в (x, y).
// Повторяет то, как библиотека рисует встроенный шрифт (Adafruit_GFX::drawChar), чтобы
// русские и латинские буквы выглядели и закрашивали фон одинаково: точка — квадрат
// size×size; если bg != color, фон под пустыми точками и в столбце просвета закрашивается,
// и строку можно печатать поверх старой без стирания. bg == color — фон не трогаем.
// У Ё и Й ещё рисуется знак над клеткой, на 2·size выше y, только цветом color:
// фон этой полосы закрашивает drawText перед каждой клеткой.
void drawRuChar(Adafruit_GFX& g, int16_t x, int16_t y, int index,
                uint16_t color, uint16_t bg, uint8_t size) {
  // Клетка вместе с полосой над ней целиком за краем — рисовать нечего, как и в библиотеке.
  if (x >= g.width() || y - 2 * size >= g.height() || x + 6 * size - 1 < 0 || y + 8 * size - 1 < 0) return;

  g.startWrite();
  for (int i = 0; i < 5; i++) {
    uint8_t line = pgm_read_byte(&FONT_RU[index * 5 + i]);
    for (int j = 0; j < 8; j++, line >>= 1) {
      if (line & 1) {
        if (size == 1) g.writePixel(x + i, y + j, color);
        else           g.writeFillRect(x + i * size, y + j * size, size, size, color);
      } else if (bg != color) {
        if (size == 1) g.writePixel(x + i, y + j, bg);
        else           g.writeFillRect(x + i * size, y + j * size, size, size, bg);
      }
    }
  }
  if (bg != color) g.writeFillRect(x + 5 * size, y, size, 8 * size, bg);   // столбец просвета

  int mark = textRuMark(index);
  if (mark >= 0) {
    int16_t top = y - 2 * size;   // строка −2, над ней ничего
    for (int i = 0; i < 5; i++) {
      uint8_t line = pgm_read_byte(&FONT_RU_MARKS[mark * 5 + i]);
      for (int j = 0; j < 2; j++, line >>= 1) {
        if (!(line & 1)) continue;
        if (size == 1) g.writePixel(x + i, top + j, color);
        else           g.writeFillRect(x + i * size, top + j * size, size, size, color);
      }
    }
  }
  g.endWrite();
}

// Рисует один знак с номером Юникода code в клетку с левым верхним углом (x, y):
// русскую букву своей таблицей, остальное встроенным шрифтом библиотеки.
void drawTextChar(Adafruit_GFX& g, int16_t x, int16_t y, uint32_t code,
                  uint16_t color, uint16_t bg, uint8_t size) {
  int ru = textRuIndex(code);
  if (ru >= 0) drawRuChar(g, x, y, ru, color, bg, size);
  else         g.drawChar(x, y, textBuiltinCode(code), color, bg, size);
}

// Сколько знаков (клеток на экране) в строке UTF-8. Не путать со strlen: тот считает байты,
// а русская буква это два байта (G67).
int textLength(const char* text) {
  int n = 0;
  while (utf8Next(text) != 0) n++;
  return n;
}

// Ширина строки на экране в точках: знаки × 6 × size. Нужна, чтобы ставить текст
// по центру или прижимать к правому краю.
int textWidth(const char* text, uint8_t size) {
  return textLength(text) * 6 * size;
}

// Печатает строку UTF-8, левый верхний угол первой буквы в (x, y). Переносов нет:
// что не влезло в экран, обрезается краем. bg == color — печать без фона, поверх картинки.
// С фоном над каждой клеткой, любой буквой, цифрой или знаком, закрашивается полоса
// высотой 2·size под точки Ё и кратку Й (см. шапку): так перерисовка «Ё» в «Е» или
// в «E» не оставляет следов.
void drawText(Adafruit_GFX& g, int16_t x, int16_t y, const char* text,
              uint8_t size, uint16_t color, uint16_t bg) {
  uint32_t code;
  while ((code = utf8Next(text)) != 0) {
    if (bg != color) g.fillRect(x, y - 2 * size, 6 * size, 2 * size, bg);
    drawTextChar(g, x, y, code, color, bg, size);
    x += 6 * size;
  }
}

// Печатает строку UTF-8 по центру ширины экрана или канвы, верх строки на высоте y.
void drawTextCentered(Adafruit_GFX& g, int16_t y, const char* text,
                      uint8_t size, uint16_t color, uint16_t bg) {
  drawText(g, (g.width() - textWidth(text, size)) / 2, y, text, size, color, bg);
}

// Копирует в out не больше maxChars знаков из text и всегда завершает out нулём.
// Режет только между буквами: если обрезать по байтам, от русской буквы может остаться
// половина, и на экране вылезет «?» (G67). Если в out не хватает места, копирует столько
// целых знаков, сколько влезло. Возвращает, сколько знаков скопировано.
int textTruncate(char* out, size_t outSize, const char* text, int maxChars) {
  if (outSize == 0) return 0;
  size_t used = 0;
  int n = 0;
  const char* p = text;
  while (n < maxChars) {
    const char* start = p;
    if (utf8Next(p) == 0) break;
    size_t len = p - start;
    if (used + len > outSize - 1) break;   // целиком не влезает — не берём совсем
    memcpy(out + used, start, len);
    used += len;
    n++;
  }
  out[used] = 0;
  return n;
}
