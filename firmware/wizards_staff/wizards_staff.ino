// Проверка BLE и датчика удара (задачи B1–B3 плана этапа 2). Посох виден как WizardsStaff,
// раз в 10 с отдаёт заряд, отвечает на info, time и roll. Удар по столу бросает кубик
// по умолчанию (d20) и отдаёт бросок в Serial и на планшет. Заодно мигает светодиодом.
// Этот файл временный, в задаче 3 основного плана его заменит настоящая прошивка.
#include "config.h"
#include "battery.h"
#include "dice.h"
#include "strike.h"
#include "ble.h"

const int     PIN_LED       = 8;      // светодиод на плате SuperMini, горит при LOW
const uint8_t DEFAULT_SIDES = 20;     // пока нет кнопок, удар бросает этот кубик

unsigned long lastBatteryAt = 0;
unsigned long lastBlinkAt   = 0;
unsigned long lastRollAt    = 0;
bool          ledOn         = false;
uint32_t      rollId        = 0;      // сквозной номер броска, пока только с включения

// Делает бросок, печатает его и отправляет на планшет.
void doRoll(uint8_t count, uint8_t sides) {
  Roll r = rollDice(count, sides);
  rollId++;

  char buf[64];
  rollBreakdown(r, buf, sizeof(buf));
  Serial.printf("Бросок #%lu: %dd%d = %d (%s)\n", (unsigned long)rollId, r.count, r.sides, r.total, buf);

  JsonDocument doc;
  doc["ev"] = "roll";
  doc["id"] = rollId;
  doc["n"]  = r.count;
  doc["d"]  = r.sides;
  JsonArray v = doc["v"].to<JsonArray>();
  for (int i = 0; i < r.count; i++) v.add(r.values[i]);
  doc["t"]  = r.total;
  doc["ts"] = 0;   // часов пока нет, планшет подставит своё время
  bleSend(doc);

  lastRollAt = millis();
  strikeReset();   // сбрасываем датчик, чтобы дрожание от этого же удара не пошло вторым броском
}

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
    // Часы DS3231 подключим следующим шагом, пока только печатаем, что пришло.
    long epoch = cmd["epoch"] | 0L;
    Serial.printf("BLE: время от телефона %ld\n", epoch);
    JsonDocument doc;
    doc["ev"]  = "ok";
    doc["cmd"] = "time";
    bleSend(doc);
    return;
  }

  if (strcmp(name, "roll") == 0) {
    // Имитация удара с планшета: для отладки приложения без стука по столу.
    int n = cmd["n"] | 1;
    int d = cmd["d"] | DEFAULT_SIDES;
    if (n < 1 || n > MAX_DICE || d < 2 || d > 1000) { bleSendError("bad roll"); return; }
    doRoll(n, d);
    return;
  }

  bleSendError("unknown cmd");
}

void setup() {
  Serial.begin(115200);
  pinMode(PIN_LED, OUTPUT);
  digitalWrite(PIN_LED, HIGH);
  delay(1500);
  Serial.println("Wizards staff: проверка BLE и датчика удара");
  batteryBegin();
  strikeBegin();
  bleBegin();
  diceSelfTest();
}

void loop() {
  bleLoop();
  unsigned long now = millis();

  // Датчик опрашиваем всегда, иначе фильтр не увидит начало пачки переключений.
  // Удар засчитываем, только если после прошлого броска прошло STRIKE_COOLDOWN_MS.
  bool strike = strikeDetected();
  if (strike && now - lastRollAt > STRIKE_COOLDOWN_MS) {
    Serial.println("Удар!");
    doRoll(1, DEFAULT_SIDES);
  }

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
  delay(2);   // датчик опрашиваем часто, чтобы не пропустить переключения
}
