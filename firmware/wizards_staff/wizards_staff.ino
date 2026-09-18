// Проверка BLE, часов и датчика удара (задачи B1–B3 плана этапа 2). Посох виден как WizardsStaff,
// раз в 10 с отдаёт заряд, отвечает на info, time, arm, disarm и roll. Приложение взводит посох
// на нужный кубик, удар об пол бросает его и отдаёт бросок в Serial и на планшет.
// Пока нет кнопок, взвод приходит только из приложения; невзведённый посох удары не считает.
// Этот файл временный, в задаче 3 основного плана его заменит настоящая прошивка.
#include <LittleFS.h>
#include "config.h"
#include "identity.h"
#include "history.h"
#include "battery.h"
#include "dice.h"
#include "strike.h"
#include "rtc.h"
#include "ble.h"

const int     PIN_LED       = 8;      // светодиод на плате SuperMini, горит при LOW
const uint8_t DEFAULT_SIDES = 20;     // кубик, который подставляется, если в команде не сказано иное

unsigned long lastBatteryAt = 0;
unsigned long lastBlinkAt   = 0;
unsigned long lastRollAt    = 0;
bool          ledOn         = false;

// Взвод: какой бросок сделает следующий удар об пол. Ноль в armedCount значит «не взведён»,
// тогда удары не считаются вовсе. Пока нет кнопок на посохе, взводит приложение командой arm.
uint8_t       armedCount = 0;
uint8_t       armedSides = 0;
unsigned long armedAt    = 0;         // когда взвели: через ARM_TIMEOUT_MS взвод сам спадает

// Рассказывает телефону, чего посох сейчас ждёт.
void sendState() {
  JsonDocument doc;
  doc["ev"] = "state";
  doc["st"] = armedCount ? "armed" : "idle";
  if (armedCount) {
    doc["n"] = armedCount;
    doc["d"] = armedSides;
  }
  bleSend(doc);
}

// Делает бросок, печатает его и отправляет на планшет.
void doRoll(uint8_t count, uint16_t sides) {
  Roll r = rollDice(count, sides);
  uint32_t id = historyNextId();        // номер сквозной, во флеш, не начинается заново
  uint32_t ts = rtcEpoch();             // ноль, если часов нет: планшет подставит своё время
  if (!historyPush(r, id, ts, 0)) Serial.println("История: бросок не записан во флеш!");

  char buf[80];
  rollBreakdown(r, buf, sizeof(buf));
  Serial.printf("Бросок #%u: %dd%d = %d (%s)\n", id, r.count, r.sides, r.total, buf);

  JsonDocument doc;
  doc["ev"] = "roll";
  doc["id"] = id;
  doc["n"]  = r.count;
  doc["d"]  = r.sides;
  JsonArray v = doc["v"].to<JsonArray>();
  for (int i = 0; i < r.count; i++) v.add(r.values[i]);
  doc["t"]  = r.total;
  doc["ts"] = ts;
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
    doc["ev"]      = "info";
    doc["fw"]      = FW_VERSION;
    doc["staffId"] = staffId;     // постоянный номер посоха, переживает выключение
    doc["mac"]     = bleMac();    // тот же адрес, что приложение видит при сканировании
    doc["hist"]    = HISTORY_FLASH_SIZE;   // ёмкость истории во флеш, а не экранной
    doc["ts"]      = rtcEpoch();  // время посоха: приложение покажет его рядом со своим
    JsonArray dice = doc["dice"].to<JsonArray>();
    for (int i = 0; i < 8; i++) dice.add(DICE_SIDES[i]);
    bleSend(doc);
    sendState();   // заодно говорим, взведён ли посох: приложение спрашивает info при подключении
    return;
  }

  if (strcmp(name, "arm") == 0) {
    // Взвод: следующий удар об пол бросит именно этот набор кубиков.
    int n = cmd["n"] | 1;
    int d = cmd["d"] | DEFAULT_SIDES;
    if (n < 1 || n > MAX_DICE || d < 2 || d > 1000) { bleSendError("bad arm"); return; }
    armedCount = n;
    armedSides = d;
    armedAt    = millis();
    strikeReset();   // забываем дрожание, накопленное до взвода
    Serial.printf("Взвод: %dd%d, жду удара\n", n, d);
    sendState();
    return;
  }

  if (strcmp(name, "disarm") == 0) {
    armedCount = 0;
    Serial.println("Взвод снят");
    sendState();
    return;
  }

  if (strcmp(name, "time") == 0) {
    // Телефон присылает секунды от 1970 года по UTC, кладём их в часы.
    long epoch = cmd["epoch"] | 0L;
    if (epoch < 1700000000L) { bleSendError("bad epoch"); return; }   // явно не наше время
    if (!rtcSetEpoch(epoch)) { bleSendError("no rtc"); return; }
    char buf[24];
    rtcFormat(rtcEpoch(), buf, sizeof(buf));
    Serial.printf("Часы: выставлены по времени телефона, теперь %s\n", buf);
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

// Готовит раздел под файлы (LittleFS) и печатает его размер. Размер нужен, чтобы выбрать
// ёмкость истории бросков в задаче B2: в плане стоит 2000 записей по 24 байта, около 48 КБ.
// Если раздел не монтируется, форматируем: после смены схемы разделов файловой системы там
// нет вовсе, и терять нечего. Замер 2026-09-18 показал именно этот случай.
void reportFlash() {
  if (!LittleFS.begin(false)) {
    Serial.println("LittleFS: раздел не смонтирован, форматирую (читаемых данных там нет)");
    if (!LittleFS.format() || !LittleFS.begin(false)) {
      Serial.println("LittleFS: ОШИБКА, раздел не поднялся даже после форматирования.");
      Serial.println("Проверь Tools -> Partition Scheme: нужен Minimal SPIFFS с разделом под файлы.");
      return;
    }
    Serial.println("LittleFS: отформатирован успешно");
  }
  Serial.printf("LittleFS: %u КБ всего, %u КБ занято, %u КБ свободно\n",
                (unsigned)(LittleFS.totalBytes() / 1024),
                (unsigned)(LittleFS.usedBytes() / 1024),
                (unsigned)((LittleFS.totalBytes() - LittleFS.usedBytes()) / 1024));
}

void setup() {
  Serial.begin(115200);
  pinMode(PIN_LED, OUTPUT);
  digitalWrite(PIN_LED, HIGH);
  delay(1500);
  Serial.println("Wizards staff: проверка BLE и датчика удара");
  reportFlash();
  historyBegin();
  batteryBegin();
  strikeBegin();
  rtcBegin();
  bleBegin();
  identityBegin();   // после bleBegin: генератор случайных чисел точнее при включённом радио
  diceSelfTest();
}

void loop() {
  bleLoop();
  unsigned long now = millis();

  // Датчик опрашиваем всегда, иначе фильтр не увидит начало пачки переключений.
  // Удар засчитываем, только если посох взведён и после прошлого броска прошло STRIKE_COOLDOWN_MS.
  bool strike = strikeDetected();

  // Итог каждой пачки переключений отправляем телефону: в журнале приложения видно,
  // что датчик вообще дёргался, и какие толчки он отбросил как слабые.
  if (strikeWindowClosed) {
    strikeWindowClosed = false;
    char msg[80];
    snprintf(msg, sizeof(msg), "Датчик: %d переключений за %lu мс, %s",
             strikeLastEdges, STRIKE_WINDOW_MS, strike ? "УДАР" : "не удар");
    bleSendLog(msg);
  }

  if (strike && now - lastRollAt > STRIKE_COOLDOWN_MS) {
    if (armedCount) {
      Serial.println("Удар!");
      doRoll(armedCount, armedSides);
      armedCount = 0;      // бросок сделан, для следующего нужен новый взвод
      sendState();
    } else {
      Serial.println("Удар, но посох не взведён: броска нет");
      bleSendLog("Удар, но посох не взведён: броска нет");
    }
  } else if (strike) {
    Serial.println("Удар, но ещё идёт пауза после прошлого броска");
    bleSendLog("Удар в паузе после прошлого броска: не считаем");
  }

  // Взвод не вечный: посох, забытый взведённым, не должен бросать от случайного стука.
  if (armedCount && now - armedAt > ARM_TIMEOUT_MS) {
    armedCount = 0;
    Serial.println("Взвод снят: слишком долго ждали удара");
    bleSendLog("Взвод снят: слишком долго ждали удара");
    sendState();
  }

  // Раз в 10 с: время и заряд в Serial, заряд телефону.
  if (now - lastBatteryAt >= BATTERY_PERIOD_MS) {
    lastBatteryAt = now;
    int mv = batteryMillivolts();
    int pct = batteryPercent();
    char clock[24];
    rtcFormat(rtcEpoch(), clock, sizeof(clock));
    Serial.printf("%s | %d mV, %d %% | BLE %s\n", clock, mv, pct,
                  bleConnected ? "connected" : "advertising");
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
