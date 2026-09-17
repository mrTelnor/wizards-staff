// Проверка BLE (задача B1 плана этапа 2): посох виден как WizardsStaff, раз в 10 с отдаёт заряд,
// отвечает на команды info и time. Заодно печатает заряд в Serial и мигает светодиодом.
// Этот файл временный, в задаче 3 основного плана его заменит настоящая прошивка.
#include "config.h"
#include "battery.h"
#include "ble.h"

const int PIN_LED = 8;   // светодиод на плате SuperMini, горит при LOW

unsigned long lastBatteryAt = 0;
unsigned long lastBlinkAt   = 0;
bool          ledOn         = false;

// Разбирает команду от телефона. Команды по протоколу из docs/ПЛАН-APP.md.
void bleOnCommand(JsonDocument& cmd) {
  const char* name = cmd["cmd"] | "";
  Serial.printf("BLE: команда %s\n", name);

  if (strcmp(name, "info") == 0) {
    JsonDocument doc;
    doc["ev"]   = "info";
    doc["fw"]   = FW_VERSION;
    doc["hist"] = HISTORY_SIZE;
    JsonArray dice = doc["dice"].to<JsonArray>();
    for (int i = 0; i < 8; i++) dice.add(DICE_SIDES[i]);
    bleSend(doc);
    return;
  }

  if (strcmp(name, "time") == 0) {
    // Часы DS3231 подключим в задаче B3, пока только печатаем, что пришло.
    long epoch = cmd["epoch"] | 0L;
    Serial.printf("BLE: время от телефона %ld\n", epoch);
    JsonDocument doc;
    doc["ev"] = "ok";
    doc["cmd"] = "time";
    bleSend(doc);
    return;
  }

  bleSendError("unknown cmd");
}

void setup() {
  Serial.begin(115200);
  pinMode(PIN_LED, OUTPUT);
  digitalWrite(PIN_LED, HIGH);
  delay(1500);
  Serial.println("Wizards staff: проверка BLE");
  batteryBegin();
  bleBegin();
}

void loop() {
  bleLoop();
  unsigned long now = millis();

  // Раз в 10 с: заряд в Serial и телефону.
  if (now - lastBatteryAt >= BATTERY_PERIOD_MS) {
    lastBatteryAt = now;
    int mv = batteryMillivolts();
    int pct = batteryPercent();
    Serial.printf("Battery: %d mV, %d %%, BLE %s\n", mv, pct, bleConnected ? "connected" : "advertising");
    bleSendBattery(mv, pct);
  }

  // Мигание: раз в секунду без телефона, часто при подключённом телефоне.
  unsigned long period = bleConnected ? 200 : 1000;
  if (now - lastBlinkAt >= period) {
    lastBlinkAt = now;
    ledOn = !ledOn;
    digitalWrite(PIN_LED, ledOn ? LOW : HIGH);
  }
  delay(5);
}
