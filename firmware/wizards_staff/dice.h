#pragma once
#include <Arduino.h>
#include "config.h"

// Бросок кубиков. Случайные числа даёт аппаратный генератор ESP32, поэтому бросок честный.

// Один бросок: сколько кубиков, каких, что выпало на каждом, сумма.
struct Roll {
  uint8_t count;
  uint8_t sides;
  uint8_t values[MAX_DICE];
  int     total;
};

// Бросает count кубиков по sides граней. count ограничен MAX_DICE.
Roll rollDice(uint8_t count, uint8_t sides) {
  Roll r;
  r.count = count > MAX_DICE ? MAX_DICE : count;
  r.sides = sides;
  r.total = 0;
  for (int i = 0; i < r.count; i++) {
    r.values[i] = random(1, sides + 1);   // от 1 до sides включительно
    r.total += r.values[i];
  }
  return r;
}

// Собирает строку вида "2+1+4" из значений броска, для Serial и экрана.
void rollBreakdown(const Roll& r, char* out, size_t len) {
  out[0] = 0;
  for (int i = 0; i < r.count; i++) {
    char part[8];
    snprintf(part, sizeof(part), i == 0 ? "%d" : "+%d", r.values[i]);
    strlcat(out, part, len);
  }
}

// Самопроверка генератора: 6000 бросков d6, печатает, сколько раз выпала каждая грань.
// Ожидаем около 1000 на каждую грань, разброс плюс-минус сотня это нормально.
void diceSelfTest() {
  int hits[7] = { 0 };
  for (int i = 0; i < 6000; i++) hits[rollDice(1, 6).values[0]]++;
  Serial.println("Самопроверка d6 x 6000:");
  for (int f = 1; f <= 6; f++) Serial.printf("  %d: %d\n", f, hits[f]);
}
