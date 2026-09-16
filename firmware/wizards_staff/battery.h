#pragma once
#include "config.h"

// Замер заряда аккумулятора через делитель 100 кОм + 100 кОм на ножке PIN_BATTERY.
// Делитель подключён к плюсу аккумулятора до тумблера, поэтому замер работает
// и при выключенном тумблере, когда плата питается от USB.

// Настраивает вход: 12-битный отсчёт и диапазон до ~2,5 В (на ножке будет 1,6–2,1 В).
void batteryBegin() {
  analogReadResolution(12);
  analogSetPinAttenuation(PIN_BATTERY, ADC_11db);
}

// Напряжение аккумулятора в милливольтах. Делитель делит пополам, поэтому умножаем на 2.
// Усредняем 8 замеров, чтобы значение не дёргалось.
int batteryMillivolts() {
  long sum = 0;
  for (int i = 0; i < 8; i++) sum += analogReadMilliVolts(PIN_BATTERY);
  return (sum / 8) * 2;
}

// Процент заряда: BATTERY_FULL_MV это 100 %, BATTERY_EMPTY_MV это 0 %. Грубо, но для значка хватает.
int batteryPercent() {
  int pct = map(batteryMillivolts(), BATTERY_EMPTY_MV, BATTERY_FULL_MV, 0, 100);
  return constrain(pct, 0, 100);
}
