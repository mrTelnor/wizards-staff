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
