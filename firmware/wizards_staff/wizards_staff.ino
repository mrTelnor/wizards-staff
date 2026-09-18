// Проверка BLE, часов и датчика удара (задачи B1–B3 плана этапа 2). Посох виден как WizardsStaff,
// раз в 10 с отдаёт заряд, отвечает на info, time, arm, disarm и roll. Приложение взводит посох
// на нужную кость, удар об пол бросает её и отдаёт бросок в Serial и на планшет.
// Пока нет кнопок, взвод приходит только из приложения; невзведённый посох удары не считает.
// Этот файл временный, в задаче 3 основного плана его заменит настоящая прошивка.
#include <LittleFS.h>
#include "config.h"
#include "log.h"
#include "identity.h"
#include "history.h"
#include "battery.h"
#include "dice.h"
#include "strike.h"
#include "rtc.h"
#include "ble.h"

const int     PIN_LED       = 8;      // светодиод на плате SuperMini, горит при LOW
const uint8_t DEFAULT_SIDES = 20;     // кость, которая подставляется, если в команде не сказано иное

unsigned long lastBatteryAt = 0;
unsigned long lastBlinkAt   = 0;
unsigned long lastRollAt    = 0;
bool          ledOn         = false;

// Отслеживание связи для журнала: пока планшета нет, строки копятся в буфере log.h,
// при подключении выливаются в приложение.
//
// Момент слива выбран по первой команде от планшета, а не по таймеру. Android подключается,
// потом ищет службы, потом подписывается на уведомления, и всё это занимает больше секунды.
// Слить раньше подписки значит отправить строки в пустоту: приложение их просто не услышит.
// А раз команда пришла, значит подписка уже есть, приложение сначала подписывается и только
// потом пишет. Таймер оставлен запасным: вдруг подключились сторонней программой и молчат.
bool          logWasConnected = false;
unsigned long logConnectedAt  = 0;
bool          logFlushPending = false;
bool          logGotCommand   = false;   // планшет заговорил, значит уведомления он уже слушает

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

// Отправляет планшету событие roll. Один вид на свежий бросок и на запись из истории:
// приложение не должно их различать, для него это просто броски с номерами.
void sendRollEvent(uint32_t id, uint32_t ts, uint8_t count, uint16_t sides,
                   const uint16_t* values, int total, uint8_t flags) {
  JsonDocument doc;
  doc["ev"] = "roll";
  doc["id"] = id;
  doc["n"]  = count;
  doc["d"]  = sides;
  JsonArray v = doc["v"].to<JsonArray>();
  for (int i = 0; i < count && i < MAX_DICE; i++) v.add(values[i]);
  doc["t"]  = total;
  doc["ts"] = ts;
  if (flags) doc["f"] = flags;   // пока всегда ноль, поле появится вместе с задачей D4
  bleSend(doc);
}

// Отправляет одну запись из истории. Вызывается из historyAfter по каждой найденной.
void sendHistoryRecord(const HistoryRecord& r) {
  sendRollEvent(r.id, r.ts, r.count, r.sides, r.values, r.total, r.flags);
}

// Делает бросок, печатает его и отправляет на планшет.
void doRoll(uint8_t count, uint16_t sides) {
  Roll r = rollDice(count, sides);
  uint32_t id = historyNextId();        // номер сквозной, во флеш, не начинается заново
  uint32_t ts = rtcEpoch();             // ноль, если часов нет: планшет подставит своё время
  if (!historyPush(r, id, ts, 0)) logLine(LOG_HISTORY, LOG_ERROR, "бросок не записан во флеш");

  char buf[80];
  rollBreakdown(r, buf, sizeof(buf));
  logLine(LOG_ROLL, LOG_INFO, "#%u: %dd%d = %d (%s)", id, r.count, r.sides, r.total, buf);

  sendRollEvent(id, ts, r.count, r.sides, r.values, r.total, 0);

  lastRollAt = millis();
  strikeReset();   // сбрасываем датчик, чтобы дрожание от этого же удара не пошло вторым броском
}

// Разбирает команду от телефона. Команды по протоколу из docs/ПЛАН-APP.md.
void bleOnCommand(JsonDocument& cmd) {
  logGotCommand = true;   // планшет заговорил: можно сливать накопленный журнал
  const char* name = cmd["cmd"] | "";
  // Только в монитор порта: в приложении эта строка всегда стоит прямо под отправленной
  // командой, которую и так видно, и не добавляет ничего.
  logLocal(LOG_BLE, LOG_INFO, "команда %s", name);

  if (strcmp(name, "info") == 0) {
    JsonDocument doc;
    doc["ev"]      = "info";
    doc["fw"]      = FW_VERSION;
    doc["staffId"] = staffId;     // постоянный номер посоха, переживает выключение
    doc["mac"]     = bleMac();    // тот же адрес, что приложение видит при сканировании
    doc["hist"]    = HISTORY_FLASH_SIZE;   // ёмкость истории во флеш, а не экранной
    doc["arm"]     = ARM_TIMEOUT_MS / 1000;   // через сколько секунд спадает взвод: приложение
                                             // показывает это число, чтобы не хранить его у себя
    doc["ts"]      = rtcEpoch();  // время посоха: приложение покажет его рядом со своим
    JsonArray dice = doc["dice"].to<JsonArray>();
    for (int i = 0; i < 8; i++) dice.add(diceSides[i]);
    bleSend(doc);

    // То же самое строками для человека: под фильтром «Инфо» машинный JSON скрыт,
    // и без них команда info выглядела бы как будто посох промолчал. Разложено по модулям,
    // чтобы каждая строка попала в тот же раздел журнала, что и её собратья при старте.
    logLine(LOG_BOOT, LOG_INFO, "прошивка %s, взвод живёт %lu с",
            FW_VERSION, ARM_TIMEOUT_MS / 1000);
    logLine(LOG_ID, LOG_INFO, "посох ID %u, MAC %s", staffId, bleMac().c_str());
    logLine(LOG_HISTORY, LOG_INFO, "%u из %u записей", historyCount, (unsigned)HISTORY_FLASH_SIZE);

    uint32_t epoch = rtcEpoch();
    if (epoch) {
      char when[24];
      rtcFormat(epoch, when, sizeof(when));
      logLine(LOG_TIME, LOG_INFO, "время на посохе %s", when);
    } else {
      logLine(LOG_TIME, LOG_WARN, "часы не выставлены, у бросков не будет времени");
    }

    char list[64];
    diceList(list, sizeof(list));
    logLine(LOG_DICE, LOG_INFO, "кости по кнопкам: %s", list);

    sendState();   // заодно говорим, взведён ли посох: приложение спрашивает info при подключении
    return;
  }

  if (strcmp(name, "arm") == 0) {
    // Взвод: следующий удар об пол бросит именно этот набор костей.
    int n = cmd["n"] | 1;
    int d = cmd["d"] | DEFAULT_SIDES;
    if (n < 1 || n > MAX_DICE || d < 2 || d > 1000) { bleSendError("bad arm"); return; }
    armedCount = n;
    armedSides = d;
    armedAt    = millis();
    strikeReset();   // забываем дрожание, накопленное до взвода
    logLine(LOG_CHARGE, LOG_INFO, "%dd%d, жду удара", n, d);
    sendState();
    return;
  }

  if (strcmp(name, "disarm") == 0) {
    armedCount = 0;
    logLine(LOG_CHARGE, LOG_INFO, "снят по команде с планшета");
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
    logSetTime(rtcEpoch());
    logLine(LOG_TIME, LOG_INFO, "выставлены по времени планшета, теперь %s", buf);
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

  if (strcmp(name, "hist") == 0) {
    // Догрузка пропущенного: отдаём броски с номером больше after, порцией.
    // В конце всегда histend, даже если порция пустая: приложению нужно знать, что мы закончили.
    uint32_t after = cmd["after"] | 0;
    HistoryPage page = historyAfter(after, HIST_BATCH_SIZE, sendHistoryRecord);
    JsonDocument doc;
    doc["ev"]   = "histend";
    doc["sent"] = page.sent;
    doc["last"] = page.lastId;   // с этим номером приложение просит следующую порцию
    doc["more"] = page.more;
    bleSend(doc);
    logLine(LOG_HISTORY, LOG_INFO, "отдано %u записей после #%u, ещё есть: %s",
            page.sent, after, page.more ? "да" : "нет");
    return;
  }

  if (strcmp(name, "map") == 0) {
    // Назначение костей кнопкам. Проверяем все восемь разом и пишем только целиком:
    // половина новой таблицы и половина старой хуже, чем отказ.
    JsonArray dice = cmd["dice"];
    if (dice.isNull() || dice.size() != 8) { bleSendError("map needs 8 dice"); return; }
    uint16_t fresh[8];
    for (int i = 0; i < 8; i++) {
      int v = dice[i] | 0;
      if (v < 2 || v > DICE_SIDES_MAX) { bleSendError("bad dice value"); return; }
      fresh[i] = (uint16_t)v;
    }
    memcpy(diceSides, fresh, sizeof(diceSides));
    diceSave();
    char list[64];
    diceList(list, sizeof(list));
    logLine(LOG_DICE, LOG_INFO, "кости переназначены: %s", list);
    JsonDocument doc;
    doc["ev"] = "map";
    doc["ok"] = true;
    bleSend(doc);
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
    logLine(LOG_FS, LOG_INFO, "раздел не смонтирован, форматирую (читаемых данных там нет)");
    if (!LittleFS.format() || !LittleFS.begin(false)) {
      logLine(LOG_FS, LOG_ERROR, "раздел не поднялся даже после форматирования, "
              "проверь Tools -> Partition Scheme: нужен Minimal SPIFFS с разделом под файлы");
      return;
    }
    logLine(LOG_FS, LOG_INFO, "отформатирован успешно");
  }
  logLine(LOG_FS, LOG_INFO, "%u КБ всего, %u КБ занято, %u КБ свободно",
          (unsigned)(LittleFS.totalBytes() / 1024),
          (unsigned)(LittleFS.usedBytes() / 1024),
          (unsigned)((LittleFS.totalBytes() - LittleFS.usedBytes()) / 1024));
}

void setup() {
  Serial.begin(115200);
  pinMode(PIN_LED, OUTPUT);
  digitalWrite(PIN_LED, HIGH);
  delay(1500);
  rtcBegin();             // первым делом часы: с этого момента у журнала есть настоящее время
  logLine(LOG_BOOT, LOG_INFO, "посох мага, прошивка %s", FW_VERSION);
  reportFlash();
  historyBegin();
  diceLoad();
  batteryBegin();
  strikeBegin();
  bleBegin();
  identityBegin();   // после bleBegin: генератор случайных чисел точнее при включённом радио
  diceSelfTest();
}

void loop() {
  bleLoop();
  unsigned long now = millis();

  // Планшет появился или пропал: включаем и выключаем отправку журнала в приложение.
  if (bleConnected != logWasConnected) {
    logWasConnected = bleConnected;
    if (bleConnected) {
      logSink         = bleSendLog;
      logConnectedAt  = now;
      logFlushPending = true;
      logGotCommand   = false;
    } else {
      logSink = nullptr;   // дальше строки копятся в буфере до следующего подключения
    }
  }
  if (logFlushPending && (logGotCommand || now - logConnectedAt >= LOG_FLUSH_FALLBACK_MS)) {
    logFlushPending = false;
    int sent = logFlush();
    // Только в монитор порта: в приложении эти строки и так сейчас появятся.
    logLocal(LOG_BLE, LOG_INFO, "журнал старта: отдано планшету %d строк (%s)", sent,
             logGotCommand ? "по первой команде" : "по запасному таймеру");
  }

  // Датчик опрашиваем всегда, иначе фильтр не увидит начало пачки переключений.
  // Удар засчитываем, только если посох взведён и после прошлого броска прошло STRIKE_COOLDOWN_MS.
  bool strike = strikeDetected();

  if (strike && now - lastRollAt > STRIKE_COOLDOWN_MS) {
    if (armedCount) {
      logLine(LOG_STRIKE, LOG_INFO, "удар засчитан, бросаю");
      doRoll(armedCount, armedSides);
      armedCount = 0;      // бросок сделан, для следующего нужен новый взвод
      sendState();
    } else {
      logLine(LOG_STRIKE, LOG_INFO, "удар не засчитан, посох не взведён");
    }
  } else if (strike) {
    logLine(LOG_STRIKE, LOG_INFO, "удар не засчитан, пауза после броска");
  }

  // Взвод не вечный: посох, забытый взведённым, не должен бросать от случайного стука.
  if (armedCount && now - armedAt > ARM_TIMEOUT_MS) {
    armedCount = 0;
    logLine(LOG_CHARGE, LOG_INFO, "снят сам: слишком долго ждали удара");
    sendState();
  }

  // Раз в 10 с: заряд в журнал и отдельным сообщением планшету для рейки.
  if (now - lastBatteryAt >= BATTERY_PERIOD_MS) {
    lastBatteryAt = now;
    int mv = batteryMillivolts();
    int pct = batteryPercent();
    logSetTime(rtcEpoch());   // заодно подтягиваем время журнала к часам, чтобы не уплывало
    logLocal(LOG_BATTERY, LOG_INFO, "%d мВ, %d %%, BLE %s", mv, pct,
             bleConnected ? "подключён" : "реклама");   // только в монитор: у приложения есть bat
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
