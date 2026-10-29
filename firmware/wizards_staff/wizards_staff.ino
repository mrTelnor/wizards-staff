// Главный цикл посоха. Посох виден по BLE как WizardsStaff, раз в 10 с отдаёт заряд,
// отвечает на info, time, arm, disarm и roll. Приложение взводит посох на нужную кость,
// удар об пол бросает её и отдаёт бросок в Serial и на планшет.
// Экран (задача 3 основного плана): в покое часы, взведённый посох показывает «3d6»,
// после удара на экране результат, через RESULT_TIMEOUT_MS снова часы.
// Взводят кнопки костей на посохе (задача 4) и приложение; невзведённый посох удары не считает.
// Взвод не спадает сам. Через SLEEP_TIMEOUT_MS без действий экран засыпает, будят его кость,
// удар по взведённому посоху и команды arm и roll с планшета.
// Служебные кнопки: «Сброс» снимает взвод (долгое гасит экран), «Повтор» взводит прошлый бросок
// (долгое — 1d20 с удачей или неудачей),
// «Тихо» выключает звук, «Свет» переключает свечение кристалла и его палитру.
// ◄ ► листают последние броски, «Отмена» выходит из истории. ОК (меню) пока только будит экран.
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
int           lastLoggedPct = -1;     // заряд, записанный в журнал последним; -1 — ещё не писали
unsigned long lastRollAt    = 0;
unsigned long lastClockAt   = 0;
unsigned long lastButtonAt  = 0;      // последнее нажатие кнопки: сразу после него удар не считаем

// Что сейчас на экране. Часы перерисовываются раз в минуту только в режиме покоя:
// результат броска должен стоять, пока посох не взведут заново.
enum ScreenMode { SCREEN_CLOCK, SCREEN_ARMED, SCREEN_RESULT, SCREEN_HISTORY };
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

// Гасит экран: подсветку и саму матрицу. why — почему, для журнала.
void sleepScreen(const char* why) {
  screenAsleep = true;
  displaySleep();
  logLine(LOG_BOOT, LOG_INFO, "экран уснул: %s", why);
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
uint8_t       armedFlags = 0;        // удача или неудача (ROLL_FORTUNE, ROLL_MISFORTUNE), только у одной кости

// Рассказывает телефону, чего посох сейчас ждёт.
void sendState() {
  JsonDocument doc;
  doc["ev"] = "state";
  doc["st"] = armedCount ? "armed" : "idle";
  if (armedCount) {
    doc["n"] = armedCount;
    doc["d"] = armedSides;
    if (armedFlags) doc["adv"] = armedFlags == ROLL_FORTUNE ? 1 : -1;
  }
  bleSend(doc);
}

// Взводит посох на n костей по d граней: экран, журнал, планшет. Общая для кнопок
// и команды arm, чтобы взвод с посоха и с планшета ничем не отличался.
// flags — удача или неудача, действует только при одной кости.
// Будит спящий экран: взвод с кнопки и с планшета должен быть виден сразу.
void armStaff(uint8_t n, uint16_t d, uint8_t flags = 0) {
  wakeScreen();
  noteActivity();
  armedCount = n;
  armedSides = d;
  armedFlags = n == 1 ? flags : 0;
  strikeReset();   // забываем дрожание, накопленное до взвода
  logLine(LOG_CHARGE, LOG_INFO, "%dd%d%s%s, жду удара", n, d,
          armedFlags ? ", " : "", rollModeName(armedFlags));
  screenArmed(n, d, armedFlags);
  screenMode = SCREEN_ARMED;
  sendState();
}

// Снимает взвод и возвращает экран к часам. Общая для кнопки «Сброс» и команды disarm.
// who — кто снял, для журнала.
void disarmStaff(const char* who) {
  armedCount = 0;
  armedFlags = 0;
  logLine(LOG_CHARGE, LOG_INFO, "снят: %s", who);
  showClock();
  sendState();
}

// Нажата кнопка кости. Та же кость добавляет к взводу ещё одну (3d6), после MAX_DICE
// счёт идёт по кругу с одной: перебрал - жми дальше, не нужно снимать взвод.
// Другая кость начинает взвод заново с одной: смешивать кости в броске нельзя (ТЗ, раздел 2).
void onDiceButton(int i) {
  uint16_t sides = diceSides[i];
  lastButtonAt = millis();
  // Сначала будим экран, потом щёлкаем. Наоборот щелчок пропадал: экран, проснувшийся
  // сразу за двухмиллисекундным щелчком, глушил его (проверено 2026-10-29, причина в железе).
  wakeScreen();
  soundClick();
  logLine(LOG_BUTTONS, LOG_INFO, "кнопка P%d, кость d%u", i, sides);
  // У взвода с удачей или неудачей кнопка той же кости не добавляет вторую, а снимает
  // режим: 1d20 обычный. Иначе пришлось бы гадать, что значит «2d20 с удачей».
  if (armedCount && armedSides == sides && !armedFlags) {
    armStaff(armedCount >= MAX_DICE ? 1 : armedCount + 1, sides);
  } else {
    armStaff(1, sides);
  }
}

// Экран, к которому посох возвращается из истории и после сна: взвод, если посох
// взведён, иначе часы. Результат прошлого броска сюда не входит: он есть в истории.
void showHomeScreen() {
  if (armedCount) {
    screenArmed(armedCount, armedSides, armedFlags);
    screenMode = SCREEN_ARMED;
  } else {
    showClock();
  }
}

// ===== История на экране: кнопки ◄ ► (SPEC 2.5) =====
// Последние HISTORY_SIZE бросков из флеш, по HISTORY_PAGE на экран, свежие сверху.
// ◄ ► двигают выделение по одной строке, список сдвигается вслед за ним тоже по одной.
// Читаются один раз при входе. Перечитывать незачем: бросок во время просмотра
// показывает свой экран результата, и история закрывается сама.
HistoryRecord historyView[HISTORY_SIZE];
int           historyViewCount = 0;
int           historyViewTop   = 0;   // какой бросок в верхней строке экрана
int           historyViewSel   = 0;   // какой бросок выделен
unsigned long historyViewAt    = 0;   // последнее нажатие в истории: от него считается выход по времени

// ◄ вне истории: открыть её, выделен самый свежий бросок.
void openHistory() {
  historyViewCount = historyLatest(historyView, HISTORY_SIZE);
  historyViewTop   = 0;
  historyViewSel   = 0;
  historyViewAt    = millis();
  screenMode       = SCREEN_HISTORY;
  screenHistory(historyView, historyViewCount, 0, 0);
  logLine(LOG_BUTTONS, LOG_INFO, "история: %d бросков", historyViewCount);
}

// Выйти из истории туда, где был: на взвод или к часам. Взвод при этом не снимается.
void closeHistory() {
  showHomeScreen();
}

// Двигает выделение: dir +1 — к старому броску (◄), -1 — к новому (►).
// ► на самом свежем выходит из истории, ◄ на самом старом ничего не делает.
// Выделение ушло за край экрана — список сдвигается на одну строку за ним.
void historyStep(int dir) {
  historyViewAt = millis();
  int next = historyViewSel + dir;
  if (next < 0) { closeHistory(); return; }
  if (next >= historyViewCount) return;
  historyViewSel = next;
  if (historyViewSel < historyViewTop) historyViewTop = historyViewSel;
  if (historyViewSel >= historyViewTop + HISTORY_PAGE) historyViewTop = historyViewSel - HISTORY_PAGE + 1;
  drawHistoryList(historyView, historyViewCount, historyViewTop, historyViewSel);
}

// Служебные кнопки, которые разбудили экран: их короткое или долгое нажатие
// в этот раз ничего не делает (SPEC 2.5: во сне служебные кнопки только будят).
uint8_t svcWokeScreen = 0;

// «Повтор»: взводит тот же бросок, что был последним (было 2d6 — снова 2d6).
// Берёт его из истории во флеш, поэтому работает и сразу после включения.
void repeatLastRoll() {
  HistoryRecord last;
  if (!historyLast(last)) {
    logLine(LOG_BUTTONS, LOG_INFO, "повторять нечего: бросков ещё не было");
    return;
  }
  armStaff(last.count, last.sides);   // режим удачи не повторяется: он сбрасывается после броска
}

// Долгое «Повтор»: взвести 1d20 с удачей, следующее долгое — с неудачей, и так по кругу
// (решение автора 2026-10-29). Обычный d20 возвращают кнопка кости и «Сброс».
void toggleFortune() {
  bool wasFortune = armedCount == 1 && armedSides == 20 && armedFlags == ROLL_FORTUNE;
  armStaff(1, 20, wasFortune ? ROLL_MISFORTUNE : ROLL_FORTUNE);
}

// «Тихо»: звук вкл/выкл. Включённый звук подтверждает себя щелчком,
// выключение подтверждает значок в строке состояния.
void toggleSound() {
  soundSetEnabled(!soundEnabled);
  drawMuteIcon();
  soundClick();
  logLine(LOG_BUTTONS, LOG_INFO, "звук %s", soundEnabled ? "включён" : "выключен");
}

// Событие служебной кнопки или кнопки меню. Что делает каждая: SPEC, раздел 2.5.
// Нажатие во сне только будит экран. Кнопки, до которых очередь ещё не дошла, пишут в журнал.
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
      showHomeScreen();
      logLine(LOG_BUTTONS, LOG_INFO, "служебная P%d (%s) разбудила экран", ev.button, name);
    } else {
      svcWokeScreen &= ~bit;
      soundClick();
    }
    return;
  }

  if (svcWokeScreen & bit) return;   // это нажатие уже потрачено на пробуждение
  bool isLong = ev.kind == SVC_LONG;
  logLine(LOG_BUTTONS, LOG_INFO, "служебная P%d (%s): %s", ev.button, name,
          isLong ? "долго" : "коротко");

  switch (ev.button) {
    case BTN_RESET:
      if (isLong)          sleepScreen("долгое «Сброс»");
      else if (screenMode == SCREEN_HISTORY) closeHistory();   // в истории «Сброс» только выходит, взвод не снимает
      else if (armedCount) disarmStaff("кнопкой «Сброс»");
      else                 showClock();   // взвода нет: просто убрать результат с экрана
      break;
    case BTN_REPEAT:
      if (isLong) toggleFortune();
      else        repeatLastRoll();
      break;
    case BTN_LIGHT:
      if (isLong) glowNextPalette();
      else        glowNextMode();
      logLine(LOG_BUTTONS, LOG_INFO, "свет: %s, %s", GLOW_MODE_NAMES[glowMode],
              GLOW_PALETTES[glowPalette].name);
      break;
    case BTN_SILENT:
      if (!isLong) toggleSound();
      break;
    case BTN_PREV:
      if (isLong) break;
      if (screenMode == SCREEN_HISTORY) historyStep(+1);
      else                              openHistory();
      break;
    case BTN_NEXT:
      if (!isLong && screenMode == SCREEN_HISTORY) historyStep(-1);
      break;
    case BTN_CANCEL:
      if (!isLong && screenMode == SCREEN_HISTORY) closeHistory();
      break;
  }
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
  // У удачи и неудачи n = 1, в v оба броска, в t выбранный, а не сумма v:
  // приложение смотрит на adv, а не считает сумму само (docs/ПЛАН-APP.md, задача D4).
  JsonArray v = doc["v"].to<JsonArray>();
  int nv = rollValueCount(count, flags);
  for (int i = 0; i < nv && i < MAX_DICE; i++) v.add(values[i]);
  doc["t"]  = total;
  doc["ts"] = ts;
  if (flags) doc["adv"] = flags == ROLL_FORTUNE ? 1 : -1;
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
  if (step != 1 && step != 3) return;   // случайные броски не трогаем
  uint16_t forced = step == 1 ? 20 : 1;
  r.values[0] = forced;
  // У удачи и неудачи подменяем оба броска, иначе вторая кость могла бы оказаться
  // лучше или хуже подменённой, и выбор перестал бы сходиться.
  if (r.flags) r.values[1] = forced;
  r.total = forced;
  logLine(LOG_ROLL, LOG_WARN, "тестовый режим: d20 подменён на %d", r.total);
}

// Делает бросок, печатает его и отправляет на планшет. Будит спящий экран:
// удар по взведённому посоху во сне сразу показывает результат.
void doRoll(uint8_t count, uint16_t sides, uint8_t flags = 0) {
  wakeScreen();
  Roll r = rollDice(count, sides, flags);
  applyTestD20(r);
  uint32_t id = historyNextId();        // номер сквозной, во флеш, не начинается заново
  uint32_t ts = rtcEpoch();             // ноль, если часов нет: планшет подставит своё время
  if (!historyPush(r, id, ts)) logLine(LOG_HISTORY, LOG_ERROR, "бросок не записан во флеш");

  char buf[80];
  rollBreakdown(r, buf, sizeof(buf));
  logLine(LOG_ROLL, LOG_INFO, "#%u: %dd%d%s%s = %d (%s)", id, r.count, r.sides,
          r.flags ? ", " : "", rollModeName(r.flags), r.total, buf);

  sendRollEvent(id, ts, r.count, r.sides, r.values, r.total, r.flags);

  screenMode = SCREEN_RESULT;
  screenResult(r);   // с анимацией барабана и тиками, около секунды; бросок планшету уже ушёл
  if (r.count == 1 && r.sides == 20 && r.total == 20)     soundCrit();
  else if (r.count == 1 && r.sides == 20 && r.total == 1) soundFail();
  else                                                    soundChord();

  lastRollAt = millis();
  noteActivity();  // отсчёт до часов и до сна идёт от момента, когда результат встал на экран
  strikeReset();   // сбрасываем датчик, чтобы дрожание от этого же удара не пошло вторым броском
}

// Поле adv в командах arm и roll: 1 удача, -1 неудача, 0 или нет поля — обычный бросок.
// Удача бывает только у одной кости. Неверное значение получает отказ, а не молча
// превращается в обычный бросок. Возвращает false, если отказ уже отправлен.
bool parseAdv(JsonDocument& cmd, int n, uint8_t& flags) {
  int adv = cmd["adv"] | 0;
  flags = adv == 1 ? ROLL_FORTUNE : adv == -1 ? ROLL_MISFORTUNE : 0;
  if (adv != 0 && !flags) {
    bleSendError("bad adv", "adv бывает 1 (удача), -1 (неудача) или 0");
    return false;
  }
  if (flags && n != 1) {
    bleSendError("bad adv", "удача и неудача бывают только у одной кости");
    return false;
  }
  return true;
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
    uint8_t flags;
    if (!parseAdv(cmd, n, flags)) return;
    armStaff(n, d, flags);
    return;
  }

  if (strcmp(name, "disarm") == 0) {
    disarmStaff("командой с планшета");
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
    uint8_t flags;
    if (!parseAdv(cmd, n, flags)) return;
    doRoll(n, d, flags);
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
  soundLoad();            // до первого экрана и первого звука: значок «Тихо» и стартовый писк зависят от него
  batteryBegin();
  displayBegin();
  statusBattPct = batteryPercent();   // до первого экрана, чтобы значок не начинался с нуля
  showClock();
  strikeBegin();
  buttonsBegin();
  bleBegin();
  identityBegin();   // после bleBegin: генератор случайных чисел точнее при включённом радио
  diceSelfTest();
  glowLoad();             // режим и палитра «Света» из флеш
  ringStartup();          // огненная вспышка и угасание: посох готов
  glowFadeIn();           // дальше разгорается свечение, если «Свет» не выключен
  soundStartup();
  noteActivity();         // отсчёт до сна с конца запуска
}

void loop() {
  bleLoop();
  ringUpdate();   // догорает вспышка кольца после броска или дышит свечение «Света»
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
      doRoll(armedCount, armedSides, armedFlags);
      armedCount = 0;      // бросок сделан, для следующего нужен новый взвод
      armedFlags = 0;      // удача и неудача тоже: режим не должен остаться включённым случайно
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

  // История без нажатий SETTINGS_TIMEOUT_MS закрывается сама. Время свежее, как выше.
  if (screenMode == SCREEN_HISTORY && millis() - historyViewAt > SETTINGS_TIMEOUT_MS) {
    closeHistory();
  }

  // Сон: SLEEP_TIMEOUT_MS без действий в любом состоянии, и взведённый посох тоже засыпает.
  // Время свежее по той же причине, что выше: lastActivityAt могли поставить в этом проходе.
  if (!screenAsleep && millis() - lastActivityAt > SLEEP_TIMEOUT_MS) {
    sleepScreen("долго без действий");
  }

  // Раз в 10 с: заряд в журнал и отдельным сообщением планшету для рейки.
  if (now - lastBatteryAt >= BATTERY_PERIOD_MS) {
    lastBatteryAt = now;
    int mv = batteryMillivolts();
    int pct = batteryPercent();
    logSetTime(rtcEpoch());   // заодно подтягиваем время журнала к часам, чтобы не уплывало
    // В журнал только заметная перемена: раз в 10 с одно и то же забивало монитор порта,
    // а шаг в 1 % давал бы ту же частоту, показания дрожат на границе процентов.
    // Только в монитор: у приложения есть bat.
    if (lastLoggedPct < 0 || abs(pct - lastLoggedPct) >= BATTERY_LOG_STEP_PCT) {
      lastLoggedPct = pct;
      logLocal(LOG_BATTERY, LOG_INFO, "%d мВ, %d %%, BLE %s", mv, pct,
               bleConnected ? "подключён" : "реклама");
    }
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
