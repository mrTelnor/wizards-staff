// Главный цикл посоха. Посох виден по BLE как WizardsStaff, раз в 10 с отдаёт заряд,
// отвечает на info, time, arm, disarm и roll. Приложение взводит посох на нужную кость,
// удар об пол бросает её и отдаёт бросок в Serial и на планшет.
// Экран (задача 3 основного плана): в покое часы, взведённый посох показывает «3d6»,
// после удара на экране результат, через RESULT_TIMEOUT_MS снова часы.
// Взводят кнопки костей на посохе (задача 4) и приложение; невзведённый посох удары не считает.
// Взвод не спадает сам. Через SLEEP_TIMEOUT_MS без действий экран засыпает, будят его кость,
// удар по взведённому посоху и команды arm и roll с планшета.
// Служебные кнопки и кнопки меню опрашиваются, но действий у них пока нет: только пробуждение экрана.
#include <LittleFS.h>
#include "config.h"
#include "log.h"
#include "identity.h"
#include "history.h"
#include "battery.h"
#include "dice.h"
#include "auth.h"
#include "strike.h"
#include "rtc.h"
#include "ble.h"
#include "display.h"
#include "buttons.h"
#include "sound.h"
#include "lights.h"

// Кость, которая подставляется, если в команде не сказано иное.
// Тип не случаен. Оператор | у ArduinoJson смотрит на тип значения справа и отдаёт
// его, если присланное число в этот тип не влезает. Пока константа была uint8_t,
// запрос d1000 молча превращался в d20: тысяча не влезает в байт. Поэтому и сама
// константа двухбайтовая, и в местах разбора стоит явное приведение к int —
// иначе d70000 снова стал бы двадцаткой вместо честного отказа.
const uint16_t DEFAULT_SIDES = 20;

unsigned long lastBatteryAt = 0;
unsigned long lastRollAt    = 0;
unsigned long lastClockAt   = 0;
unsigned long lastButtonAt  = 0;      // последнее нажатие кнопки: сразу после него удар не считаем

// Что сейчас на экране. Часы перерисовываются раз в минуту только в режиме покоя:
// результат броска должен стоять, пока посох не взведут заново.
enum ScreenMode { SCREEN_CLOCK, SCREEN_ARMED, SCREEN_RESULT };
ScreenMode    screenMode    = SCREEN_CLOCK;
long          clockMinute   = -1;     // минута, которая сейчас нарисована на экране

// Возвращает экран к часам. Нужна в трёх местах: при старте, при снятии взвода
// с планшета и когда результат простоял RESULT_TIMEOUT_MS.
void showClock() {
  long epoch = rtcEpoch();
  clockMinute = epoch / 60;
  screenClock(epoch);
  screenMode = SCREEN_CLOCK;
}

// Сон экрана. Отсчёт SLEEP_TIMEOUT_MS идёт от последнего действия: нажатия кнопки,
// взвода, броска. Спит только экран, плата работает: слушает кнопки, датчик и планшет.
bool          screenAsleep   = false;
unsigned long lastActivityAt = 0;

// Запоминает, что посохом только что пользовались: сон откладывается.
void noteActivity() {
  lastActivityAt = millis();
}

// Будит экран, если он спит. Картинку рисует тот, кто будил: взвод, бросок.
void wakeScreen() {
  if (!screenAsleep) return;
  screenAsleep = false;
  displayWake();
  logLine(LOG_BOOT, LOG_INFO, "экран проснулся");
}

// Гасит экран: подсветку и саму матрицу.
void sleepScreen() {
  screenAsleep = true;
  displaySleep();
  logLine(LOG_BOOT, LOG_INFO, "экран уснул: %lu с без действий", SLEEP_TIMEOUT_MS / 1000);
}

// Отслеживание связи для журнала: пока планшета нет, строки идут только в монитор порта.
bool          logWasConnected = false;

// Гейт авторизации. Живёт ровно один сеанс связи: отключился - значит в следующий раз
// назовёт PIN заново. Правила в docs/ПЛАН-APP.md, раздел «Авторизация».
bool          authed     = false;   // этот сеанс связи назвал верный PIN
unsigned long authSince  = 0;       // когда подключились: от этого мгновения идут AUTH_TIMEOUT_MS
bool          authClosed = false;   // связь уже разорвана за молчание, второй раз не рвём

// Взвод: какой бросок сделает следующий удар об пол. Ноль в armedCount значит «не взведён»,
// тогда удары не считаются вовсе. Взводят кнопки костей и приложение командой arm.
// Взвод живёт, пока его не сбросят или не бросят (решение автора 2026-10-28, раньше спадал за 5 мин).
uint8_t       armedCount = 0;
uint16_t      armedSides = 0;        // два байта: кости бывают до d1000, в байт такая не влезет

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

// Взводит посох на n костей по d граней: экран, журнал, планшет. Общая для кнопок
// и команды arm, чтобы взвод с посоха и с планшета ничем не отличался.
// Будит спящий экран: взвод с кнопки и с планшета должен быть виден сразу.
void armStaff(uint8_t n, uint16_t d) {
  wakeScreen();
  noteActivity();
  armedCount = n;
  armedSides = d;
  strikeReset();   // забываем дрожание, накопленное до взвода
  logLine(LOG_CHARGE, LOG_INFO, "%dd%d, жду удара", n, d);
  screenArmed(n, d);
  screenMode = SCREEN_ARMED;
  sendState();
}

// Нажата кнопка кости. Та же кость добавляет к взводу ещё одну (3d6), после MAX_DICE
// счёт идёт по кругу с одной: перебрал - жми дальше, не нужно снимать взвод.
// Другая кость начинает взвод заново с одной: смешивать кости в броске нельзя (ТЗ, раздел 2).
void onDiceButton(int i) {
  uint16_t sides = diceSides[i];
  lastButtonAt = millis();
  soundClick();
  logLine(LOG_BUTTONS, LOG_INFO, "кнопка P%d, кость d%u", i, sides);
  if (armedCount && armedSides == sides) {
    armStaff(armedCount >= MAX_DICE ? 1 : armedCount + 1, sides);
  } else {
    armStaff(1, sides);
  }
}

// Служебные кнопки, которые разбудили экран: их короткое или долгое нажатие
// в этот раз ничего не делает (SPEC 2.5: во сне служебные кнопки только будят).
uint8_t svcWokeScreen = 0;

// Событие служебной кнопки или кнопки меню. Пока только будит экран и пишет в журнал,
// действия кнопок появятся следующими шагами плана.
void onServiceEvent(const ServiceEvent& ev) {
  uint8_t bit = 1 << ev.button;
  const char* name = SERVICE_NAMES[ev.button];
  noteActivity();

  if (ev.kind == SVC_DOWN) {
    lastButtonAt = millis();   // нажатие трясёт посох: удар сразу после него не считаем
    if (screenAsleep) {
      svcWokeScreen |= bit;
      wakeScreen();
      // Во сне на экране могли быть только часы или взвод: результат к этому времени
      // уже сменился часами (RESULT_TIMEOUT_MS короче SLEEP_TIMEOUT_MS).
      if (armedCount) { screenArmed(armedCount, armedSides); screenMode = SCREEN_ARMED; }
      else            showClock();
      logLine(LOG_BUTTONS, LOG_INFO, "служебная P%d (%s) разбудила экран", ev.button, name);
    } else {
      svcWokeScreen &= ~bit;
      soundClick();
    }
    return;
  }

  if (svcWokeScreen & bit) return;   // это нажатие уже потрачено на пробуждение
  logLine(LOG_BUTTONS, LOG_INFO, "служебная P%d (%s): %s", ev.button, name,
          ev.kind == SVC_LONG ? "долго" : "коротко");
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

// Тестовый режим d20 (TEST_D20_CYCLE в config.h): подменяет итог броска 1d20 по кругу
// «случайный, 20, случайный, 1». Остальные броски не трогает.
uint32_t testD20Rolls = 0;   // сколько бросков 1d20 было с включения

void applyTestD20(Roll& r) {
  if (!TEST_D20_CYCLE || r.count != 1 || r.sides != 20) return;
  int step = testD20Rolls++ % 4;
  if (step == 1) r.values[0] = 20;
  if (step == 3) r.values[0] = 1;
  r.total = r.values[0];
  if (step == 1 || step == 3) {
    logLine(LOG_ROLL, LOG_WARN, "тестовый режим: d20 подменён на %d", r.total);
  }
}

// Делает бросок, печатает его и отправляет на планшет. Будит спящий экран:
// удар по взведённому посоху во сне сразу показывает результат.
void doRoll(uint8_t count, uint16_t sides) {
  wakeScreen();
  Roll r = rollDice(count, sides);
  applyTestD20(r);
  uint32_t id = historyNextId();        // номер сквозной, во флеш, не начинается заново
  uint32_t ts = rtcEpoch();             // ноль, если часов нет: планшет подставит своё время
  if (!historyPush(r, id, ts, 0)) logLine(LOG_HISTORY, LOG_ERROR, "бросок не записан во флеш");

  char buf[80];
  rollBreakdown(r, buf, sizeof(buf));
  logLine(LOG_ROLL, LOG_INFO, "#%u: %dd%d = %d (%s)", id, r.count, r.sides, r.total, buf);

  sendRollEvent(id, ts, r.count, r.sides, r.values, r.total, 0);

  screenMode = SCREEN_RESULT;
  screenResult(r);   // с анимацией барабана и тиками, около секунды; бросок планшету уже ушёл
  if (r.count == 1 && r.sides == 20 && r.total == 20)     soundCrit();
  else if (r.count == 1 && r.sides == 20 && r.total == 1) soundFail();
  else                                                    soundChord();

  lastRollAt = millis();
  noteActivity();  // отсчёт до часов и до сна идёт от момента, когда результат встал на экран
  strikeReset();   // сбрасываем датчик, чтобы дрожание от этого же удара не пошло вторым броском
}

// Разбирает команду от телефона. Команды по протоколу из docs/ПЛАН-APP.md.
void bleOnCommand(JsonDocument& cmd) {
  const char* name = cmd["cmd"] | "";
  // Только в монитор порта: в приложении эта строка всегда стоит прямо под отправленной
  // командой, которую и так видно, и не добавляет ничего.
  logLocal(LOG_BLE, LOG_INFO, "команда %s", name);

  // Вход по PIN. Отвечаем на неё всегда: это единственная дверь внутрь.
  if (strcmp(name, "auth") == 0) {
    if (!authIsSet()) {
      // Защиты нет - считаем, что дверь и так открыта. Отвечаем успехом, чтобы
      // приложению не приходилось разбирать особый случай: оно просто шлёт auth всегда.
      authed = true;
      JsonDocument doc;
      doc["ev"] = "auth";
      doc["ok"] = true;
      bleSend(doc);
      logLine(LOG_AUTH, LOG_INFO, "вход открыт: PIN на посохе не задан");
      return;
    }

    uint32_t wait = authWaitSeconds();
    if (wait) {
      JsonDocument doc;
      doc["ev"]   = "auth";
      doc["ok"]   = false;
      doc["wait"] = wait;
      bleSend(doc);
      logLine(LOG_AUTH, LOG_WARN, "попытка входа во время паузы, ждать ещё %u с", wait);
      return;
    }

    const char* pin = cmd["pin"] | "";
    JsonDocument doc;
    doc["ev"] = "auth";
    if (authTry(pin)) {          // промах authTry записывает в журнал сама
      authed = true;
      doc["ok"] = true;
      bleSend(doc);
      logLine(LOG_AUTH, LOG_INFO, "вход разрешён");
    } else {
      doc["ok"] = false;
      uint32_t after = authWaitSeconds();
      if (after) doc["wait"] = after;
      bleSend(doc);
    }
    return;
  }

  // Пока PIN не назван, наружу выдаём только то, что и так видно при сканировании:
  // какой это посох и знает ли планшет его PIN. Этого хватает, чтобы приложение
  // подставило сохранённый PIN, и не хватает ни для чего другого.
  if (strcmp(name, "info") == 0 && authIsSet() && !authed) {
    JsonDocument doc;
    doc["ev"]      = "info";
    doc["fw"]      = FW_VERSION;
    doc["staffId"] = staffId;
    doc["mac"]     = bleMac();
    doc["pinset"]  = true;
    bleSend(doc);
    logLine(LOG_AUTH, LOG_INFO, "сведения выданы сокращённо: вход ещё не открыт");
    return;
  }

  // Всё остальное закрыто, включая roll, arm и disarm. Настоящая пакость за игровым
  // столом - это подкинутый бросок или снятый в нужный момент взвод, а не смена
  // настроек подсветки, поэтому оставлять их открытыми ради удобства отладки незачем.
  if (authIsSet() && !authed) {
    char why[BLE_ERROR_TEXT];
    snprintf(why, sizeof(why), "команда %s закрыта: сначала вход по PIN командой auth", name);
    bleSendError("auth required", why);
    return;
  }

  if (strcmp(name, "info") == 0) {
    JsonDocument doc;
    doc["ev"]      = "info";
    doc["fw"]      = FW_VERSION;
    doc["staffId"] = staffId;     // постоянный номер посоха, переживает выключение
    doc["mac"]     = bleMac();    // тот же адрес, что приложение видит при сканировании
    doc["pinset"]  = authIsSet();          // задан ли на посохе PIN
    doc["authed"]  = authed;               // открыт ли вход в этом сеансе связи
    doc["hist"]    = HISTORY_FLASH_SIZE;   // ёмкость истории во флеш, а не экранной
    doc["arm"]     = 0;   // через сколько секунд спадает взвод; 0 — не спадает (с 2026-10-28)
    doc["ts"]      = rtcEpoch();  // время посоха: приложение покажет его рядом со своим
    JsonArray dice = doc["dice"].to<JsonArray>();
    for (int i = 0; i < 8; i++) dice.add(diceSides[i]);
    bleSend(doc);

    // То же самое строками для человека: под фильтром «Инфо» машинный JSON скрыт,
    // и без них команда info выглядела бы как будто посох промолчал. Разложено по модулям,
    // чтобы каждая строка попала в тот же раздел журнала, что и её собратья при старте.
    logLine(LOG_BOOT, LOG_INFO, "прошивка %s, взвод не спадает, сон экрана через %lu с",
            FW_VERSION, SLEEP_TIMEOUT_MS / 1000);
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
    int d = cmd["d"] | (int)DEFAULT_SIDES;   // приведение обязательно, см. DEFAULT_SIDES
    if (n < 1 || n > MAX_DICE || d < 2 || d > 1000) {
      char why[BLE_ERROR_TEXT];
      snprintf(why, sizeof(why), "взвод %dd%d невозможен: костей от 1 до %d, граней от 2 до 1000",
               n, d, MAX_DICE);
      bleSendError("bad arm", why);
      return;
    }
    armStaff(n, d);
    return;
  }

  if (strcmp(name, "disarm") == 0) {
    armedCount = 0;
    logLine(LOG_CHARGE, LOG_INFO, "снят по команде с планшета");
    showClock();
    sendState();
    return;
  }

  if (strcmp(name, "time") == 0) {
    // Телефон присылает секунды от 1970 года по UTC, кладём их в часы.
    long epoch = cmd["epoch"] | 0L;
    // Число меньше этого - ноябрь 2023 года и раньше, то есть явно не наше время.
    if (epoch < 1700000000L) {
      bleSendError("bad epoch", "время не принято: прислано число раньше ноября 2023 года");
      return;
    }
    if (!rtcSetEpoch(epoch)) {
      bleSendError("no rtc", "часы DS3231 не отвечают, время не записано");
      return;
    }
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
    int d = cmd["d"] | (int)DEFAULT_SIDES;   // приведение обязательно, см. DEFAULT_SIDES
    if (n < 1 || n > MAX_DICE || d < 2 || d > 1000) {
      char why[BLE_ERROR_TEXT];
      snprintf(why, sizeof(why), "бросок %dd%d невозможен: костей от 1 до %d, граней от 2 до 1000",
               n, d, MAX_DICE);
      bleSendError("bad roll", why);
      return;
    }
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
    if (dice.isNull() || dice.size() != 8) {
      char why[BLE_ERROR_TEXT];
      snprintf(why, sizeof(why), "в команде map должно быть ровно 8 костей, пришло %u",
               (unsigned)(dice.isNull() ? 0 : dice.size()));
      bleSendError("map needs 8 dice", why);
      return;
    }
    uint16_t fresh[8];
    for (int i = 0; i < 8; i++) {
      int v = dice[i] | 0;
      if (v < 2 || v > DICE_SIDES_MAX) {
        char why[BLE_ERROR_TEXT];
        snprintf(why, sizeof(why), "кость номер %d задана как d%d, разрешено от d2 до d%u",
                 i + 1, v, (unsigned)DICE_SIDES_MAX);
        bleSendError("bad dice value", why);
        return;
      }
      fresh[i] = (uint16_t)v;
    }
    // Порядок наводим сами, повторы отвергаем: восемь кнопок с двумя одинаковыми
    // костями смысла не имеют, а выглядят как опечатка в команде.
    uint16_t twice = diceNormalize(fresh);
    if (twice) {
      char why[BLE_ERROR_TEXT];
      snprintf(why, sizeof(why), "кость d%u задана дважды, все восемь должны быть разными",
               (unsigned)twice);
      bleSendError("duplicate dice", why);
      return;
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

  if (strcmp(name, "pin") == 0) {
    // Смена PIN. Старый обязателен, даже когда вход уже открыт: планшет могли оставить
    // без присмотра разблокированным, и тогда чужому хватило бы одной команды.
    const char* why = authChange(cmd["old"] | "", cmd["new"] | "");
    if (why) { bleSendError("pin rejected", why); return; }
    JsonDocument doc;
    doc["ev"]  = "ok";
    doc["cmd"] = "pin";
    bleSend(doc);
    return;
  }

  // Пустое имя значит, что поля cmd в присланном JSON не было вовсе: это другая ошибка,
  // и человеку полезнее услышать про неё, а не про незнакомую команду с пустым именем.
  char why[BLE_ERROR_TEXT];
  if (name[0]) snprintf(why, sizeof(why), "команда %s посоху незнакома", name);
  else         snprintf(why, sizeof(why), "в присланном JSON нет поля cmd");
  bleSendError("unknown cmd", why);
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

// Пишет в журнал, почему плата перезапустилась. Нужна, чтобы отличить обычное включение
// от просадки питания: при просадке (brownout) плата молча перезапускается, и снаружи
// это выглядит как долгий старт или зависание.
void reportResetReason() {
  esp_reset_reason_t why = esp_reset_reason();
  const char* text;
  switch (why) {
    case ESP_RST_POWERON:   text = "включение питания"; break;
    case ESP_RST_SW:        text = "перезапуск из программы (например, после заливки)"; break;
    case ESP_RST_EXT:       text = "кнопка RESET"; break;
    case ESP_RST_BROWNOUT:  text = "ПРОСАДКА ПИТАНИЯ (brownout)"; break;
    case ESP_RST_PANIC:     text = "сбой прошивки (panic)"; break;
    case ESP_RST_INT_WDT:
    case ESP_RST_TASK_WDT:
    case ESP_RST_WDT:       text = "сторожевой таймер: прошивка зависла"; break;
    case ESP_RST_USB:       text = "перезапуск по USB"; break;
    default:                text = "другая"; break;
  }
  bool bad = why == ESP_RST_BROWNOUT || why == ESP_RST_PANIC
          || why == ESP_RST_INT_WDT || why == ESP_RST_TASK_WDT || why == ESP_RST_WDT;
  logLine(LOG_BOOT, bad ? LOG_WARN : LOG_INFO, "причина запуска: %s (код %d)", text, (int)why);
}

void setup() {
  soundBegin();           // первым делом ноль на ножке пищалки, иначе транзистор может приоткрыться
  ringBegin();            // и сразу гасим кольцо: до этого оно светит мусором от загрузчика
  Serial.begin(115200);
  delay(1500);
  rtcBegin();             // первым делом часы: с этого момента у журнала есть настоящее время
  logLine(LOG_BOOT, LOG_INFO, "посох мага, прошивка %s", FW_VERSION);
  reportResetReason();
  if (TEST_D20_CYCLE) {
    logLine(LOG_BOOT, LOG_WARN, "ТЕСТОВЫЙ РЕЖИМ d20: случайный, 20, случайный, 1 по кругу. "
            "Перед игрой TEST_D20_CYCLE = false в config.h");
  }
  reportFlash();
  historyBegin();
  diceLoad();
  authBegin();
  batteryBegin();
  displayBegin();
  statusBattPct = batteryPercent();   // до первого экрана, чтобы значок не начинался с нуля
  showClock();
  strikeBegin();
  buttonsBegin();
  bleBegin();
  identityBegin();   // после bleBegin: генератор случайных чисел точнее при включённом радио
  diceSelfTest();
  ringStartup();          // огненная вспышка и угасание: посох готов, дальше кольцо не горит
  soundStartup();
  noteActivity();         // отсчёт до сна с конца запуска
}

void loop() {
  bleLoop();
  ringUpdate();   // догорает вспышка кольца после броска, если она ещё идёт
  unsigned long now = millis();

  // Планшет появился или пропал: включаем и выключаем отправку журнала в приложение.
  // Накопленного журнала нет: то, что напечаталось до подключения, осталось в мониторе порта.
  if (bleConnected != logWasConnected) {
    logWasConnected = bleConnected;
    logSink = bleConnected ? bleSendLog : nullptr;
    displaySetConnected(bleConnected);
    // Каждый новый сеанс связи начинается с закрытой двери, даже если предыдущий
    // её открыл. Отсчёт времени на ввод PIN идёт с этого мгновения.
    authed     = false;
    authClosed = false;
    authSince  = now;
  }

  // Молчит и не называется - закрываемся сами. Ждать, пока чужой надумает уйти,
  // незачем: пока он на связи, посох занят и планшет к нему не подключится.
  if (bleConnected && authIsSet() && !authed && !authClosed
      && now - authSince > AUTH_TIMEOUT_MS) {
    authClosed = true;
    JsonDocument doc;
    doc["ev"] = "auth";
    doc["ok"] = false;
    bleSend(doc);
    logLine(LOG_AUTH, LOG_WARN, "PIN не назван за %lu с, разрываю связь",
            AUTH_TIMEOUT_MS / 1000);
    bleDisconnect();
  }

  // Датчик опрашиваем всегда, иначе фильтр не увидит начало пачки переключений.
  // Удар засчитываем, только если посох взведён и после прошлого броска прошло STRIKE_COOLDOWN_MS.
  bool strike = strikeDetected();

  int dice = buttonsPollDice();
  if (dice >= 0) onDiceButton(dice);

  ServiceEvent svc = buttonsPollService();
  if (svc.button >= 0) onServiceEvent(svc);

  // Нажатие кнопки само трясёт посох: удар в первые STRIKE_GUARD_MS после него не считаем.
  if (strike && lastButtonAt && millis() - lastButtonAt < STRIKE_GUARD_MS) {
    logLine(LOG_STRIKE, LOG_INFO, "удар не засчитан, только что нажали кнопку");
  } else if (strike && now - lastRollAt > STRIKE_COOLDOWN_MS) {
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

  // Результат не вечный: через RESULT_TIMEOUT_MS без нового взвода экран
  // возвращается к часам. lastRollAt ставится после анимации, так что отсчёт идёт
  // от момента, когда сумма встала на экране. Время берём свежее, а не now: бросок
  // в этом же проходе цикла ставит lastRollAt позже now, и разность ушла бы через
  // ноль в огромное число — результат тут же сменился бы часами.
  if (screenMode == SCREEN_RESULT && millis() - lastRollAt > RESULT_TIMEOUT_MS) {
    showClock();
  }

  // Сон: SLEEP_TIMEOUT_MS без действий в любом состоянии, и взведённый посох тоже засыпает.
  // Время свежее по той же причине, что выше: lastActivityAt могли поставить в этом проходе.
  if (!screenAsleep && millis() - lastActivityAt > SLEEP_TIMEOUT_MS) {
    sleepScreen();
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
    displaySetBattery(pct);
  }

  // Время на экране: раз в секунду спрашиваем DS3231 (чаще незачем, а шина I2C
  // не бесплатна для опроса датчика), перерисовываем только когда сменилась минута:
  // крупные часы в покое или мелкое время в углу на остальных экранах.
  // Во сне не рисуем: проснувшись, первый же проход увидит сменившуюся минуту и дорисует.
  if (now - lastClockAt >= 1000) {
    lastClockAt = now;
    long epoch = rtcEpoch();
    if (!screenAsleep && epoch / 60 != clockMinute) {
      clockMinute = epoch / 60;
      displayTick(epoch, screenMode == SCREEN_CLOCK);
    }
  }
  delay(2);   // датчик опрашиваем часто, чтобы не пропустить переключения
}
