#pragma once
#include <Adafruit_NeoPixel.h>
#include <Preferences.h>
#include "config.h"

// Кольцо WS2812B на 12 светодиодов, подсветка кристалла. Здесь только кольцо:
// что и когда светить, решает главный цикл.
//
// Ножка кольца GPIO21 у ESP32-C3 — это ещё и выход TX порта UART0. Загрузчик платы
// при каждом включении печатает туда служебный текст, и кольцо принимает его за цвета:
// загорается случайным, обычно ярким белым. Помешать загрузчику нельзя, поэтому
// кольцо гасим самым первым делом в setup(). Короткая вспышка при включении
// питания остаётся, её прикрывает стартовая анимация.

Adafruit_NeoPixel ring(RING_LEDS, PIN_RING, NEO_GRB + NEO_KHZ800);

// Стартовая анимация: огонь разгорается, вспыхивает и гаснет.
const unsigned long RING_FIRE_RISE_MS = 700;    // разгорается до вспышки
const unsigned long RING_FIRE_FADE_MS = 1100;   // угасает до темноты
const unsigned long RING_FRAME_MS     = 20;     // кадр, 50 кадров в секунду

// Гасит кольцо и забирает его ножку у UART0. Вызывать в setup() сразу после soundBegin(),
// до всего долгого: пока ножку не перехватили, кольцо светит мусором от загрузчика.
void ringBegin() {
  ring.begin();
  ring.setBrightness(RING_BRIGHTNESS_MAX);
  ring.clear();
  ring.show();
}

// Цвет огня по «накалу» от 0 до 255: чёрный → тёмно-красный → оранжевый → жёлтый.
// Белого не бывает: синяя составляющая всегда ноль, огонь тёплый.
uint32_t fireColor(uint8_t heat) {
  if (heat < 128) return ring.Color(heat * 2, 0, 0);              // разгорается красный
  return ring.Color(255, (heat - 128) * 3 / 2, 0);                // к нему добавляется зелёный, выходит оранжевый и жёлтый
}

// Огненная вспышка при включении: около 1,8 с. Каждый светодиод мерцает сам по себе,
// общий накал сначала растёт, на вершине огонь жёлтый, потом остывает через
// оранжевый и красный до темноты. После анимации кольцо выключено.
// Блокирующая: вызывается в конце setup(), когда посоху ещё нечего ждать.
void ringStartup() {
  unsigned long total = RING_FIRE_RISE_MS + RING_FIRE_FADE_MS;
  unsigned long startedAt = millis();
  for (unsigned long t = 0; t < total; t = millis() - startedAt) {
    // Общий накал от 0 до 1: быстро вверх, плавно вниз. Квадрат на спаде
    // делает угасание мягким: долго тлеет и только в конце темнеет.
    float level;
    if (t < RING_FIRE_RISE_MS) {
      level = (float)t / RING_FIRE_RISE_MS;
    } else {
      float left = 1.0f - (float)(t - RING_FIRE_RISE_MS) / RING_FIRE_FADE_MS;
      level = left * left;
    }
    for (int i = 0; i < RING_LEDS; i++) {
      int flicker = random(70, 101);   // каждый огонёк от 70 до 100 % общего накала
      ring.setPixelColor(i, fireColor(level * 255 * flicker / 100));
    }
    ring.show();
    delay(RING_FRAME_MS);
  }
  ring.clear();
  ring.show();
}

// ===== Вспышка при итоге броска =====
// В отличие от стартовой анимации не блокирует: вспышку запускает ringFlash(), а кадры
// дорисовывает ringUpdate() из главного цикла и ringWait() из пауз между нотами.
// Так золотая вспышка горит прямо под фанфару, а не после неё.

enum RingFlash { RING_FLASH_NONE, RING_FLASH_WHITE, RING_FLASH_CRIT, RING_FLASH_FAIL };

// Как вспышка меняется во времени: нарастает за rise, держится hold, гаснет за fade, всё в мс.
struct RingFlashShape { unsigned long rise, hold, fade; };
const RingFlashShape RING_SHAPE_WHITE = {  0,  40,  300 };   // обычный бросок: короткая белая
const RingFlashShape RING_SHAPE_CRIT  = { 60, 600,  900 };   // 20 на d20: золотая, ровная
const RingFlashShape RING_SHAPE_FAIL  = { 60, 2300, 1200 };  // 1 на d20: красная, мерцает как огонь, гаснет вместе с фразой провала

RingFlash     ringFlashKind = RING_FLASH_NONE;   // какая вспышка идёт сейчас
unsigned long ringFlashAt   = 0;                 // когда началась
unsigned long ringFrameAt   = 0;                 // когда рисовали последний кадр

// ===== Свечение кристалла: кнопка «Свет» =====
// Огонь, как при старте, только спокойный и своего цвета. Гаснет только кнопкой «Свет»:
// сон экрана и взвод его не трогают (решение автора 2026-10-29). Вспышки бросков
// идут поверх свечения, догорев, отдают кольцо ему обратно.
// Режим и палитра помнятся во флеш.

enum GlowMode { GLOW_OFF, GLOW_DIM, GLOW_BRIGHT };
const char* const GLOW_MODE_NAMES[3] = { "выкл", "тускло", "ярко" };

// Палитра: цвет остывшего огонька и цвет раскалённого. Каждый светодиод переливается
// между ними по своему накалу. Белый взят тёплым: холодный у WS2812B синеват и тянет больше тока.
struct GlowPalette { uint8_t cold[3], hot[3]; const char* name; };
const GlowPalette GLOW_PALETTES[] = {
  { { 255,   0,   0 }, { 255, 100,   0 }, "красный - оранжевый" },
  { { 255, 220, 160 }, { 255, 190,   0 }, "белый - жёлтый" },
  { {   0, 120,   0 }, { 100, 255,  60 }, "тёмно-зелёный - светло-зелёный" },
  { {   0, 170, 255 }, {   0,   0, 255 }, "голубой - синий" },
  { { 200,   0, 255 }, {  90,   0, 160 }, "фиолетовый - тёмно-фиолетовый" },
};
const int GLOW_PALETTE_COUNT = sizeof(GLOW_PALETTES) / sizeof(GLOW_PALETTES[0]);

const unsigned long GLOW_FADE_IN_MS = 400;   // разгорается после включения, после вспышки броска и после старта
const int           GLOW_STEP       = 5;     // накал проходит 1/5 пути к цели за кадр: огонь дышит, а не дёргается

GlowMode      glowMode    = GLOW_OFF;
uint8_t       glowPalette = 0;
unsigned long glowSince   = 0;               // с какого мгновения разгорается
uint8_t       glowHeat[RING_LEDS];           // накал каждого огонька, от 0 до 255
uint8_t       glowTarget[RING_LEDS];         // к какому накалу он сейчас ползёт

// Достаёт режим и палитру из флеш и раскладывает огоньки по случайному накалу.
// Вызывать в setup() до ringStartup().
void glowLoad() {
  Preferences prefs;
  prefs.begin("staff", true);
  glowMode    = (GlowMode)prefs.getUChar("light", GLOW_OFF);
  glowPalette = prefs.getUChar("palette", 0);
  prefs.end();
  if (glowMode > GLOW_BRIGHT) glowMode = GLOW_OFF;
  if (glowPalette >= GLOW_PALETTE_COUNT) glowPalette = 0;
  for (int i = 0; i < RING_LEDS; i++) {
    glowHeat[i]   = random(60, 256);
    glowTarget[i] = random(60, 256);
  }
}

// Запоминает режим и палитру во флеш. Пишется только по нажатию «Свет».
void glowSave() {
  Preferences prefs;
  prefs.begin("staff", false);
  prefs.putUChar("light", glowMode);
  prefs.putUChar("palette", glowPalette);
  prefs.end();
}

// Начать разгораться с темноты: после старта, после вспышки броска, при включении.
void glowFadeIn() {
  glowSince = millis();
}

// Один кадр свечения. Накал каждого огонька ползёт к своей случайной цели, дошёл —
// получает новую. От накала зависит и цвет (от остывшего к раскалённому), и яркость:
// раскалённый огонёк в два раза ярче остывшего.
void glowFrame(unsigned long now) {
  float fade = (float)(now - glowSince) / GLOW_FADE_IN_MS;
  if (fade > 1.0f) fade = 1.0f;
  uint8_t level = glowMode == GLOW_BRIGHT ? GLOW_BRIGHT_LEVEL : GLOW_DIM_LEVEL;
  const GlowPalette& p = GLOW_PALETTES[glowPalette];

  for (int i = 0; i < RING_LEDS; i++) {
    int h = glowHeat[i];
    h += (glowTarget[i] - h) / GLOW_STEP;
    if (abs(glowTarget[i] - h) < GLOW_STEP) glowTarget[i] = random(60, 256);
    glowHeat[i] = h;

    float k = h / 255.0f;
    float bright = level / 255.0f * fade * (0.5f + 0.5f * k);
    uint8_t c[3];
    for (int j = 0; j < 3; j++) c[j] = (p.cold[j] + (p.hot[j] - p.cold[j]) * k) * bright;
    ring.setPixelColor(i, ring.Color(c[0], c[1], c[2]));
  }
  ring.show();
}

// Кнопка «Свет», короткое нажатие: выкл → тускло → ярко → выкл.
void glowNextMode() {
  glowMode = (GlowMode)((glowMode + 1) % 3);
  if (glowMode == GLOW_DIM) glowFadeIn();          // из темноты разгорается, из тускло в ярко переходит сразу
  if (glowMode == GLOW_OFF && ringFlashKind == RING_FLASH_NONE) {
    ring.clear();                                  // во время вспышки не гасим: она догорит и погасит сама
    ring.show();
  }
  glowSave();
}

// Кнопка «Свет», долгое нажатие: следующая палитра. Если свет выключен, включается
// тускло: иначе смену цвета не увидеть.
void glowNextPalette() {
  glowPalette = (glowPalette + 1) % GLOW_PALETTE_COUNT;
  if (glowMode == GLOW_OFF) {
    glowMode = GLOW_DIM;
    glowFadeIn();
  }
  glowSave();
}

// Яркость вспышки от 0 до 1 через t мс после начала, или -1, когда вспышка кончилась.
// На спаде квадрат: свет долго тает и только в конце гаснет, глазу так мягче.
float ringFlashLevel(const RingFlashShape& s, unsigned long t) {
  if (t < s.rise) return (float)t / s.rise;
  t -= s.rise;
  if (t < s.hold) return 1.0f;
  t -= s.hold;
  if (t < s.fade) {
    float left = 1.0f - (float)t / s.fade;
    return left * left;
  }
  return -1.0f;
}

// Рисует очередной кадр вспышки или свечения, если пора. Вызывать часто: из loop() и из ringWait().
// Кадры не чаще RING_FRAME_MS: чаще глаз не заметит, а шине кольца лишняя работа.
void ringUpdate() {
  unsigned long now = millis();
  if (now - ringFrameAt < RING_FRAME_MS) return;
  if (ringFlashKind == RING_FLASH_NONE) {
    if (glowMode != GLOW_OFF) {
      ringFrameAt = now;
      glowFrame(now);
    }
    return;
  }
  ringFrameAt = now;

  const RingFlashShape& shape = ringFlashKind == RING_FLASH_CRIT ? RING_SHAPE_CRIT
                              : ringFlashKind == RING_FLASH_FAIL ? RING_SHAPE_FAIL
                              : RING_SHAPE_WHITE;
  float level = ringFlashLevel(shape, now - ringFlashAt);
  if (level < 0) {                 // догорела: гасим кольцо, свечение (если включено) разгорится заново
    ringFlashKind = RING_FLASH_NONE;
    ring.clear();
    ring.show();
    glowFadeIn();
    return;
  }

  for (int i = 0; i < RING_LEDS; i++) {
    uint32_t c;
    if (ringFlashKind == RING_FLASH_WHITE) {
      uint8_t v = level * 255;
      c = ring.Color(v, v, v);
    } else if (ringFlashKind == RING_FLASH_CRIT) {
      c = ring.Color(level * 255, level * 170, 0);              // золото: красный и чуть меньше зелёного
    } else {
      int flicker = random(50, 101);                            // каждый огонёк от 50 до 100 %
      c = ring.Color(level * 255 * flicker / 100, 0, 0);
    }
    ring.setPixelColor(i, c);
  }
  ring.show();
}

// Запускает вспышку и сразу рисует первый кадр. Новая вспышка заменяет недогоревшую.
void ringFlash(RingFlash kind) {
  ringFlashKind = kind;
  ringFlashAt   = millis();
  ringFrameAt   = ringFlashAt - RING_FRAME_MS;   // чтобы ringUpdate нарисовал кадр не дожидаясь
  ringUpdate();
}

// Пауза на ms миллисекунд, во время которой кольцо продолжает вспышку.
// Ей заменяется delay() там, где посох ждёт посреди вспышки: ноты и рост суммы на экране.
void ringWait(unsigned long ms) {
  unsigned long startedAt = millis();
  while (millis() - startedAt < ms) {
    ringUpdate();
    delay(1);
  }
}
