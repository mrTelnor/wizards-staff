#pragma once
#include <Adafruit_NeoPixel.h>
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
const RingFlashShape RING_SHAPE_FAIL  = { 60, 900, 1200 };   // 1 на d20: красная, мерцает как огонь, гаснет вместе с тромбоном

RingFlash     ringFlashKind = RING_FLASH_NONE;   // какая вспышка идёт сейчас
unsigned long ringFlashAt   = 0;                 // когда началась
unsigned long ringFrameAt   = 0;                 // когда рисовали последний кадр

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

// Рисует очередной кадр вспышки, если пора. Вызывать часто: из loop() и из ringWait().
// Кадры не чаще RING_FRAME_MS: чаще глаз не заметит, а шине кольца лишняя работа.
void ringUpdate() {
  if (ringFlashKind == RING_FLASH_NONE) return;
  unsigned long now = millis();
  if (now - ringFrameAt < RING_FRAME_MS) return;
  ringFrameAt = now;

  const RingFlashShape& shape = ringFlashKind == RING_FLASH_CRIT ? RING_SHAPE_CRIT
                              : ringFlashKind == RING_FLASH_FAIL ? RING_SHAPE_FAIL
                              : RING_SHAPE_WHITE;
  float level = ringFlashLevel(shape, now - ringFlashAt);
  if (level < 0) {                 // догорела: гасим кольцо и больше не рисуем
    ringFlashKind = RING_FLASH_NONE;
    ring.clear();
    ring.show();
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
