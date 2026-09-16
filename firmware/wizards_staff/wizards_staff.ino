// Проверка питания (задача 10 плана): раз в секунду печатаем напряжение аккумулятора и процент заряда,
// заодно мигаем встроенным светодиодом, чтобы было видно, что плата жива.
// Этот файл временный, в задаче 3 его заменит настоящая прошивка.
#include "config.h"
#include "battery.h"

const int PIN_LED = 8;   // светодиод на плате SuperMini, горит при LOW

void setup() {
  Serial.begin(115200);
  pinMode(PIN_LED, OUTPUT);
  batteryBegin();
}

void loop() {
  digitalWrite(PIN_LED, LOW);
  Serial.printf("Battery: %d mV, %d %%\n", batteryMillivolts(), batteryPercent());
  delay(500);
  digitalWrite(PIN_LED, HIGH);
  delay(500);
}
