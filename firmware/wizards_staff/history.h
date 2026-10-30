#pragma once
#include <Arduino.h>
#include <LittleFS.h>
#include <Preferences.h>
#include <stddef.h>
#include <esp_rom_crc.h>
#include "config.h"
#include "dice.h"
#include "log.h"
#include "flashfs.h"

// История бросков во флеш. Нужна, чтобы броски, сделанные без планшета, не пропадали:
// приложение при подключении догрузит всё, что пропустило.
//
// Устройство: файл постоянного размера, записи фиксированной длины, пишем по кругу.
// Дошли до конца - начинаем сначала поверх самого старого броска.
//
// Номер броска (id) сквозной и не начинается заново при выключении: на нём держится
// опознание бросков в базе планшета. Он лежит в двух местах сразу - в NVS и в самом
// файле - и при старте берётся больший из двух. Так номер переживает потерю любого
// одного из хранилищ: NVS стирается при Erase All Flash, файл - при форматировании
// LittleFS. Порознь каждое из этих событий уже случалось.
//
// Сохранность (с 0.22.0).
// - У каждой записи контрольная сумма, и каждая проверяется по смыслу: сколько костей,
//   сколько граней, что выпало, сходится ли итог. Битая запись просто пропускается — её нет
//   ни в выдаче на планшет, ни на экране истории, ни в «Повторе», и на поиск последнего
//   номера она не влияет.
// - Рабочий файл /rolls.bin никогда не открывается на запись с обрезкой. Новый файл
//   пишется как /rolls.new и становится рабочим, только если рабочего точно нет.
// - Есть ли файл, смотрим по списку каталога (fsHasFile), а не через exists(): в
//   arduino-esp32 exists() — это попытка открыть, и разовый сбой выглядел бы как «нет».
// - Запись во флеш в arduino-esp32 ложится только при close(), а ошибки close() и flush()
//   не видны. Поэтому всё важное после записи перечитывается и сверяется.
// - Нечитаемый файл не стирается. Если на разделе есть место, он откладывается
//   как /rolls.old1…; файл полного размера на разделе 128 КБ отложить почти никогда
//   не выйдет — тогда история выключается, а файл остаётся как есть (firmware/README.md).

static const char*    HISTORY_PATH       = "/rolls.bin";
static const char*    HISTORY_NEW_PATH   = "/rolls.new";   // новый файл, пока не стал рабочим
static const char*    HISTORY_OLD_PREFIX = "/rolls.old";   // отложенные нечитаемые файлы: /rolls.old1 … /rolls.oldN
static const uint32_t HISTORY_MAGIC      = 0x48525357;     // "WSRH", чтобы не принять чужой файл за свой
static const uint16_t HISTORY_FORMAT     = 2;              // версия формата записи, см. комментарий к HistoryRecord
static const uint16_t HISTORY_FORMAT_1   = 1;              // старый формат без контрольной суммы, переводится сам

// Шапка файла. Версия формата обязательна: состав записи ещё будет меняться
// (преимущество и помеха в задаче D4), и без версии старый файл прочитается как мусор.
struct HistoryHeader {
  uint32_t magic;
  uint16_t format;
  uint16_t capacity;
};

// Одна запись, ровно 36 байт. Значения и грани по два байта ради костей до d1000.
// Байт flags — удача и неудача (ROLL_FORTUNE, ROLL_MISFORTUNE в dice.h, с 0.16.0).
// У удачи и неудачи count 1, а в values два числа.
// id == 0 означает пустую ячейку: настоящие номера начинаются с единицы.
//
// Формат 2 (с 0.22.0): в последних двух байтах контрольная сумма CRC-16 первых 34 байт.
// В формате 1 эти два байта были пустым местом под выравнивание, и что в них лежит,
// никто не обещал. Поэтому размер записи не поменялся, а файл формата 1 при старте
// переводится на месте: каждая запись проверяется по смыслу и получает свою сумму
// (historyMigrate). Записи при этом не теряются.
struct HistoryRecord {
  uint32_t id;
  uint32_t ts;
  uint16_t values[MAX_DICE];
  uint16_t total;
  uint16_t sides;
  uint8_t  count;
  uint8_t  flags;
  uint16_t crc;
};
static_assert(sizeof(HistoryRecord) == 36, "Размер записи истории изменился: подними HISTORY_FORMAT");
static_assert(offsetof(HistoryRecord, crc) == 34, "Сумма должна лежать в бывших байтах выравнивания");

uint32_t historyLastIdValue = 0;    // номер последнего броска
uint16_t historyNextSlot    = 0;    // куда писать следующий бросок
uint16_t historyCount       = 0;    // сколько ячеек занято целыми записями
uint16_t historyBadCount    = 0;    // сколько битых записей нашлось при старте
uint8_t  historyPushFails   = 0;    // сколько бросков подряд не легли во флеш
// История выключена из-за непригодного файла (не читается или чужой, отложить некуда,
// новый не завёлся). Только в этом случае fsformat можно и при смонтированном разделе.
bool     historyFileUnusable = false;
bool     historyReady       = false;
// Что не так с историей во флеш, словами для журнала и info. nullptr — всё хорошо.
// При historyReady == false это причина, почему история выключена; при true —
// разовая беда (последний бросок не лёг), история при этом ещё работает.
const char* historyProblem  = nullptr;
static const char* HISTORY_PUSH_PROBLEM = "последний бросок не лёг во флеш";

// Последний бросок этого включения в оперативной памяти. Нужен «Повтору»: так он работает,
// даже когда флеш недоступна, и не читает файл на каждое нажатие.
HistoryRecord historyRamLast  = {};
bool          historyRamValid = false;

// Сколько байт занимает ячейка номер slot.
static uint32_t historyOffset(uint16_t slot) {
  return sizeof(HistoryHeader) + (uint32_t)slot * sizeof(HistoryRecord);
}

// Текст беды для журнала: никогда не nullptr, чтобы %s не получил пустой указатель.
const char* historyProblemText() {
  return historyProblem ? historyProblem : "";
}

// Контрольная сумма записи: CRC-16 из ПЗУ ESP32 по всем полям, кроме самой суммы.
static uint16_t historyCrc(const HistoryRecord& r) {
  return esp_rom_crc16_le(0, (const uint8_t*)&r, offsetof(HistoryRecord, crc));
}

// Проверяет запись по смыслу, без контрольной суммы: такое могло получиться только
// из настоящего броска. Нужна и для новых записей, и для перевода старого формата,
// где суммы нет. Пустую ячейку (id 0) не принимает.
static bool historySane(const HistoryRecord& r) {
  if (r.id == 0) return false;
  if (r.count < 1 || r.count > MAX_DICE) return false;
  if (r.sides < 2 || r.sides > DICE_SIDES_MAX) return false;
  if (r.flags != 0 && r.flags != ROLL_FORTUNE && r.flags != ROLL_MISFORTUNE) return false;
  if (r.flags && r.count != 1) return false;   // удача и неудача бывают только у одной кости
  int n = rollValueCount(r.count, r.flags);
  uint32_t sum = 0;
  for (int i = 0; i < n; i++) {
    if (r.values[i] < 1 || r.values[i] > r.sides) return false;
    sum += r.values[i];
  }
  // Итог: у обычного броска сумма, у удачи лучший из двух, у неудачи худший.
  uint16_t a = r.values[0], b = r.values[1];
  uint32_t expect = r.flags == ROLL_FORTUNE    ? (a > b ? a : b)
                  : r.flags == ROLL_MISFORTUNE ? (a < b ? a : b)
                  : sum;
  return r.total == expect;
}

// Целая ли запись формата 2: смысл и контрольная сумма.
static bool historyRecordOk(const HistoryRecord& r) {
  return historySane(r) && r.crc == historyCrc(r);
}

// Читает ячейку slot из открытого файла как есть. false — не прочиталась целиком.
static bool historyReadSlot(File& f, uint16_t slot, HistoryRecord& r) {
  if (!f.seek(historyOffset(slot))) return false;
  return f.read((uint8_t*)&r, sizeof(r)) == sizeof(r);
}

// Скрыта ли запись проверочным режимом HISTORY_TEST_BAD_ID.
static bool historyTestHidden(const HistoryRecord& r) {
  return HISTORY_TEST_BAD_ID && r.id == HISTORY_TEST_BAD_ID;
}

// Можно ли отдать запись наружу: на планшет, на экран истории, в «Повтор».
// Целая и не скрыта проверочным режимом. Поиск последнего номера и следующей ячейки
// идёт по historyRecordOk, без проверочного режима: иначе скрытую самую свежую запись
// затёр бы следующий бросок, а проверка обещает ничего не портить во флеш.
static bool historyShowable(const HistoryRecord& r) {
  return historyRecordOk(r) && !historyTestHidden(r);
}

// Помечает историю во флеш как выключенную и пишет почему. Броски при этом
// продолжаются: номер идёт через NVS, на планшет они уходят как обычно.
static void historyDisable(const char* why) {
  historyReady   = false;
  historyProblem = why;
  logLine(LOG_HISTORY, LOG_ERROR, "история во флеш выключена: %s", why);
  logLine(LOG_HISTORY, LOG_ERROR, "броски идут на планшет, но на посохе после выключения их не будет");
}

// Записывает номер последнего броска в NVS и проверяет, что записался.
static bool historySaveId(uint32_t id) {
  Preferences prefs;
  if (!prefs.begin("staff", false)) return false;
  bool ok = prefs.putUInt("rollid", id) == sizeof(uint32_t);
  prefs.end();
  return ok;
}

// Удаляет недописанный новый файл. Удаляет только HISTORY_NEW_PATH и ничего больше:
// рабочий файл этой функцией стереть нельзя по построению.
static void historyRemoveNew() {
  if (!LittleFS.remove(HISTORY_NEW_PATH)) {
    logLine(LOG_HISTORY, LOG_WARN, "недописанный %s не удалился", HISTORY_NEW_PATH);
  }
}

// Создаёт файл заново: шапка и HISTORY_FLASH_SIZE пустых записей.
// Пишет его как HISTORY_NEW_PATH, проверяет размер и шапку после перечитывания
// и только потом переименовывает в рабочий — и только если рабочего по списку
// каталога точно нет. Так ни один сбой не обрежет существующую историю.
static bool historyCreate() {
  FsEntry leftover = fsHasFile(HISTORY_NEW_PATH);
  if (leftover == FS_ENTRY_UNKNOWN) return false;
  if (leftover == FS_ENTRY_YES) historyRemoveNew();   // от прошлой оборванной попытки

  File f = LittleFS.open(HISTORY_NEW_PATH, FILE_WRITE);
  if (!f) return false;
  HistoryHeader h = { HISTORY_MAGIC, HISTORY_FORMAT, HISTORY_FLASH_SIZE };
  bool ok = f.write((uint8_t*)&h, sizeof(h)) == sizeof(h);
  HistoryRecord empty;
  memset(&empty, 0, sizeof(empty));   // memset, а не {}: байты выравнивания тоже должны быть нулями
  for (uint16_t i = 0; ok && i < HISTORY_FLASH_SIZE; i++) {
    ok = f.write((uint8_t*)&empty, sizeof(empty)) == sizeof(empty);
  }
  f.close();
  if (ok) {
    // Запись в буфер ещё не значит запись во флеш: закрепляется она при закрытии,
    // и его ошибок не видно. Поэтому размер и шапку проверяем, открыв файл заново.
    HistoryHeader back = {};
    File check = LittleFS.open(HISTORY_NEW_PATH, FILE_READ);
    ok = check && check.size() == historyOffset(HISTORY_FLASH_SIZE)
         && check.read((uint8_t*)&back, sizeof(back)) == sizeof(back)
         && memcmp(&back, &h, sizeof(h)) == 0;
    if (check) check.close();
  }
  if (!ok) { historyRemoveNew(); return false; }

  if (fsHasFile(HISTORY_PATH) != FS_ENTRY_NO) {
    logLine(LOG_HISTORY, LOG_WARN, "рабочий файл истории нашёлся, новый на его место не ставлю");
    historyRemoveNew();
    return false;
  }
  if (!LittleFS.rename(HISTORY_NEW_PATH, HISTORY_PATH)) { historyRemoveNew(); return false; }
  if (fsHasFile(HISTORY_PATH) != FS_ENTRY_YES) return false;
  historyNextSlot = 0;
  historyCount    = 0;
  return true;
}

// Переводит файл формата 1 в формат 2 на месте, в три шага.
// 1) Каждой записи, целой по смыслу, дописывает контрольную сумму; битые по смыслу
//    оставляет как есть (их не будет видно). Байты записей, кроме суммы, не трогает.
// 2) Перечитывает файл и сверяет, что все целые по смыслу записи подписаны.
// 3) Только тогда пишет шапку с новой версией и перечитывает её.
// Шапка последней и только после сверки: файл формата 2 всегда с подписанными записями,
// иначе свежие неподписанные считались бы битыми и следующий бросок лёг бы поверх них.
// Пропало питание или что-то не сошлось — файл остаётся формата 1, история выключена
// до следующего старта, и перевод пройдёт заново: подписанные записи от этого не портятся.
static bool historyMigrate() {
  File f = LittleFS.open(HISTORY_PATH, "r+");
  if (!f) return false;
  uint16_t signedCount = 0, badCount = 0;
  bool ok = true;
  for (uint16_t i = 0; ok && i < HISTORY_FLASH_SIZE; i++) {
    HistoryRecord r;
    if (!historyReadSlot(f, i, r)) { ok = false; break; }
    if (r.id == 0) continue;
    if (!historySane(r)) {
      badCount++;
      if (badCount <= HISTORY_BAD_LOG_MAX) {
        logLine(LOG_HISTORY, LOG_WARN, "перевод: #%u в ячейке %u не сходится по смыслу, оставляю", r.id, i);
      }
      continue;
    }
    r.crc = historyCrc(r);
    if (!f.seek(historyOffset(i) + offsetof(HistoryRecord, crc))) { ok = false; break; }
    ok = f.write((uint8_t*)&r.crc, sizeof(r.crc)) == sizeof(r.crc);
    signedCount++;
    // Закрепляем порциями: LittleFS пишет копированием блоков, и без этого держал бы
    // в запасе место под весь файл сразу.
    if (signedCount % HISTORY_MIGRATE_FLUSH_EVERY == 0) f.flush();
  }
  f.close();
  if (!ok) return false;

  // Шаг 2: сверка подписей по перечитанному файлу.
  f = LittleFS.open(HISTORY_PATH, FILE_READ);
  if (!f) return false;
  uint16_t unsignedCount = 0;
  for (uint16_t i = 0; ok && i < HISTORY_FLASH_SIZE; i++) {
    HistoryRecord r;
    if (!historyReadSlot(f, i, r)) { ok = false; break; }
    if (historySane(r) && r.crc != historyCrc(r)) unsignedCount++;
  }
  f.close();
  if (!ok || unsignedCount) {
    logLine(LOG_HISTORY, LOG_ERROR, "перевод: подписи не легли (%u записей), повторю при старте", unsignedCount);
    return false;
  }

  // Шаг 3: шапка.
  f = LittleFS.open(HISTORY_PATH, "r+");
  if (!f) return false;
  HistoryHeader h = { HISTORY_MAGIC, HISTORY_FORMAT, HISTORY_FLASH_SIZE };
  ok = f.seek(0) && f.write((uint8_t*)&h, sizeof(h)) == sizeof(h);
  f.close();
  HistoryHeader back = {};
  File check = LittleFS.open(HISTORY_PATH, FILE_READ);
  ok = ok && check && check.read((uint8_t*)&back, sizeof(back)) == sizeof(back)
       && memcmp(&back, &h, sizeof(h)) == 0;
  if (check) check.close();
  if (!ok) {
    logLine(LOG_HISTORY, LOG_ERROR, "перевод: шапка формата %u не легла, повторю при старте",
            (unsigned)HISTORY_FORMAT);
    return false;
  }
  logLine(LOG_HISTORY, badCount ? LOG_WARN : LOG_INFO,
          "переведён на формат %u: подписано %u, не сошлись по смыслу %u",
          (unsigned)HISTORY_FORMAT, signedCount, badCount);
  return true;
}

// Откладывает нечитаемый файл истории под свободным именем /rolls.old1 … /rolls.oldN,
// чтобы на его месте завести новый. Ничего не стирает, прежние отложенные тоже остаются.
// Откладывает, только если есть свободное имя (не больше HISTORY_OLD_MAX) и на разделе
// хватит места под новый файл с запасом HISTORY_FREE_RESERVE: переименование места
// не освобождает. На разделе 128 КБ рядом с файлом полного размера места почти никогда
// не хватает — тогда false, причина в fail, и файл остаётся на месте нетронутым.
static bool historyPutAside(const char* why, const char*& fail) {
  logLine(LOG_HISTORY, LOG_ERROR, "файл истории не читается: %s", why);
  char name[24] = "";
  for (int i = 1; i <= HISTORY_OLD_MAX && !name[0]; i++) {
    char probe[24];
    snprintf(probe, sizeof(probe), "%s%d", HISTORY_OLD_PREFIX, i);
    FsEntry e = fsHasFile(probe);
    if (e == FS_ENTRY_UNKNOWN) { fail = "каталог не читается, файл истории оставлен как есть"; return false; }
    if (e == FS_ENTRY_NO) strlcpy(name, probe, sizeof(name));
  }
  if (!name[0]) {
    fail = "отложенных файлов уже предел, файл истории оставлен как есть";
    return false;
  }
  size_t freeBytes = LittleFS.totalBytes() - LittleFS.usedBytes();
  if (freeBytes < historyOffset(HISTORY_FLASH_SIZE) + HISTORY_FREE_RESERVE) {
    fail = "нет места отложить нечитаемый файл, он оставлен как есть";
    return false;
  }
  if (fsHasFile(name) != FS_ENTRY_NO) {   // перепроверка прямо перед переименованием
    fail = "имя для отложенного файла занято, файл истории оставлен как есть";
    return false;
  }
  if (!LittleFS.rename(HISTORY_PATH, name)) {
    fail = "нечитаемый файл не переименовался, он оставлен как есть";
    return false;
  }
  logLine(LOG_HISTORY, LOG_WARN, "нечитаемый файл отложен в %s, завожу новый", name);
  return true;
}

// Что показала сверка файла с шапкой.
enum HistoryFileState {
  HFILE_NONE,      // файла нет
  HFILE_V1,        // наш, старый формат 1
  HFILE_V2,        // наш, текущий формат
  HFILE_FOREIGN,   // не наш: не тот размер, чужая шапка, другая ёмкость или версия
  HFILE_FLAKY      // каталог или файл не читается, либо ответы при повторах разные
};

// Одна сверка файла с шапкой. Причину для журнала кладёт в why.
static HistoryFileState historyCheckOnce(const char*& why) {
  FsEntry e = fsHasFile(HISTORY_PATH);
  if (e == FS_ENTRY_UNKNOWN) { why = "каталог не читается"; return HFILE_FLAKY; }
  if (e == FS_ENTRY_NO) return HFILE_NONE;
  File f = LittleFS.open(HISTORY_PATH, FILE_READ);
  if (!f) { why = "не открывается"; return HFILE_FLAKY; }
  HistoryHeader h = {};
  bool sizeOk = f.size() == historyOffset(HISTORY_FLASH_SIZE);
  bool readOk = f.read((uint8_t*)&h, sizeof(h)) == sizeof(h);
  f.close();
  if (!sizeOk)                          { why = "не тот размер"; return HFILE_FOREIGN; }
  if (!readOk)                          { why = "шапка не дочиталась"; return HFILE_FLAKY; }
  if (h.magic != HISTORY_MAGIC)         { why = "чужая шапка"; return HFILE_FOREIGN; }
  if (h.capacity != HISTORY_FLASH_SIZE) { why = "другая ёмкость"; return HFILE_FOREIGN; }
  if (h.format == HISTORY_FORMAT)   return HFILE_V2;
  if (h.format == HISTORY_FORMAT_1) return HFILE_V1;
  why = "незнакомая версия формата";
  return HFILE_FOREIGN;
}

// Сверяет файл с шапкой HISTORY_OPEN_TRIES раз. Наш файл (формат 1 или 2) — сразу ответ:
// целая шапка разовым сбоем не бывает. «Нет файла» и «не наш» принимаются, только если
// все попытки ответили одинаково и по одной причине: разовый сбой (например, f.size()
// вернул ноль) не повод ни заводить новый файл, ни откладывать рабочий. Ответы разные —
// HFILE_FLAKY, и файл не трогаем.
static HistoryFileState historyCheckFile(const char*& why) {
  HistoryFileState first = HFILE_FLAKY;
  const char* firstWhy = "";
  bool same = true;
  for (int i = 0; i < HISTORY_OPEN_TRIES; i++) {
    if (i) delay(FS_MOUNT_RETRY_MS);
    const char* w = "";
    HistoryFileState st = historyCheckOnce(w);
    if (st == HFILE_V1 || st == HFILE_V2) return st;
    if (i == 0) { first = st; firstWhy = w; }
    else if (st != first || strcmp(w, firstWhy) != 0) same = false;
  }
  if (!same) { why = "при повторах ответы разные"; return HFILE_FLAKY; }
  why = firstWhy;
  return first;
}

// Обходит файл формата 2: считает целые и битые записи, находит самую свежую
// и ячейку для следующей. Битые на поиск последнего номера не влияют.
static bool historyScan() {
  File f = LittleFS.open(HISTORY_PATH, FILE_READ);
  if (!f) return false;
  uint32_t maxId = 0;
  uint16_t maxSlot = 0;
  for (uint16_t i = 0; i < HISTORY_FLASH_SIZE; i++) {
    HistoryRecord r;
    if (!historyReadSlot(f, i, r)) { f.close(); return false; }
    if (r.id == 0) continue;
    if (!historyRecordOk(r)) {
      historyBadCount++;
      if (historyBadCount <= HISTORY_BAD_LOG_MAX) {
        logLine(LOG_HISTORY, LOG_WARN, "битая запись в ячейке %u (номер в ней %u), пропускаю", i, r.id);
      }
      continue;
    }
    // Скрытая проверочным режимом запись цела: считается и участвует в поиске последнего
    // номера и следующей ячейки, её только не отдают наружу (historyShowable).
    if (historyTestHidden(r)) {
      logLine(LOG_HISTORY, LOG_WARN, "проверочный режим: #%u в ячейке %u скрыта, во флеш цела", r.id, i);
    }
    historyCount++;
    if (r.id > maxId) { maxId = r.id; maxSlot = i; }
  }
  f.close();
  historyLastIdValue = maxId;
  historyNextSlot    = maxId ? (uint16_t)((maxSlot + 1) % HISTORY_FLASH_SIZE) : 0;
  return true;
}

// Поднимает файл истории на смонтированном разделе. Возвращает, получилось ли;
// при неудаче уже вызвана historyDisable. savedId — номер из NVS: по нему видно,
// что броски уже были, и тогда пропажа файла — повод для предупреждения.
static bool historyOpenFile(uint32_t savedId) {
  const char* why = "";
  HistoryFileState file = historyCheckFile(why);
  if (file == HFILE_FLAKY) {
    // Файл есть или может быть, но не читается и с повторов. Не трогаем его вовсе.
    logLine(LOG_HISTORY, LOG_ERROR, "файл истории: %s, не трогаю", why);
    historyDisable("файл истории не читается, оставлен как есть");
    historyFileUnusable = true;
    return false;
  }
  if (file == HFILE_FOREIGN) {
    const char* fail = "";
    if (!historyPutAside(why, fail)) { historyDisable(fail); historyFileUnusable = true; return false; }
  }
  if (file == HFILE_NONE) {
    if (fsJustFormatted)                 logLine(LOG_HISTORY, LOG_INFO, "раздел только что стёрт, создаю файл истории");
    else if (savedId || fsWasReadyAtBoot) logLine(LOG_HISTORY, LOG_WARN, "файл истории пропал, хотя броски уже были: создаю новый");
    else                                 logLine(LOG_HISTORY, LOG_INFO, "файла истории нет, создаю");
  }
  if (file == HFILE_NONE || file == HFILE_FOREIGN) {
    if (!historyCreate()) { historyDisable("новый файл истории не создан"); historyFileUnusable = true; return false; }
  }
  if (file == HFILE_V1) {
    logLine(LOG_HISTORY, LOG_INFO, "файл формата 1, перевожу на формат %u без потери записей",
            (unsigned)HISTORY_FORMAT);
    if (!historyMigrate()) { historyDisable("перевод на формат 2 не удался, повторю при старте"); return false; }
  }
  if (!historyScan()) { historyDisable("файл истории не читается целиком"); return false; }
  return true;
}

// Готовит историю к работе: поднимает файл, находит место для следующей записи
// и восстанавливает сквозной номер. Вызывать в setup() после fsBegin(), и ещё раз
// после форматирования командой fsformat.
void historyBegin() {
  historyReady     = false;
  historyProblem   = nullptr;
  historyCount     = 0;
  historyBadCount  = 0;
  historyNextSlot  = 0;
  historyPushFails = 0;
  historyFileUnusable = false;
  // historyLastIdValue не обнуляем: при повторном вызове номер должен идти дальше.
  if (HISTORY_TEST_BAD_ID) {
    logLine(LOG_HISTORY, LOG_WARN, "ПРОВЕРОЧНЫЙ РЕЖИМ: #%u не отдаётся наружу, во флеш цела", HISTORY_TEST_BAD_ID);
    logLine(LOG_HISTORY, LOG_WARN, "после проверки HISTORY_TEST_BAD_ID = 0 в config.h");
  }

  // Номер из NVS: он мог уйти вперёд, если файл потеряли, а настройки остались.
  // Нужен и без флеш-истории: номера бросков на планшете не должны повторяться.
  Preferences prefs;
  bool nvsOk = prefs.begin("staff", false);
  uint32_t saved = nvsOk ? prefs.getUInt("rollid", 0) : 0;
  if (nvsOk) prefs.end();
  else logLine(LOG_HISTORY, LOG_ERROR, "NVS не открылся, номер броска беру только из файла");

  if (!fsMounted) historyDisable("раздел под файлы не смонтирован");
  else historyReady = historyOpenFile(saved);

  if (saved > historyLastIdValue) historyLastIdValue = saved;
  if (nvsOk && saved != historyLastIdValue && !historySaveId(historyLastIdValue)) {
    logLine(LOG_HISTORY, LOG_ERROR, "номер %u не записался в NVS", historyLastIdValue);
  }

  if (!historyReady) return;
  if (historyBadCount > HISTORY_BAD_LOG_MAX) {
    logLine(LOG_HISTORY, LOG_WARN, "и ещё битых записей: %u", historyBadCount - HISTORY_BAD_LOG_MAX);
  }
  logLine(LOG_HISTORY, historyBadCount ? LOG_WARN : LOG_INFO, "%u из %u записей, битых %u, последний номер %u",
          historyCount, (unsigned)HISTORY_FLASH_SIZE, historyBadCount, historyLastIdValue);
}

// Всё ли хорошо с историей во флеш: она включена и последний бросок лёг.
// Если нет, причина в historyProblem. Уходит в info полем histok.
bool historyAvailable() {
  return historyReady && !historyProblem;
}

// Выдаёт номер для нового броска и сразу запоминает его во флеш.
// Про износ: NVS раскидывает записи по странице, стирание выходит раз на сотню бросков,
// ресурс порядка ста тысяч стираний - это десятки тысяч игровых вечеров.
// Номер не записался в NVS — ERROR: он всё равно лежит в записи броска в файле,
// но если и файл недоступен, после перезапуска номера могут повториться.
uint32_t historyNextId() {
  historyLastIdValue++;
  if (!historySaveId(historyLastIdValue)) {
    logLine(LOG_HISTORY, LOG_ERROR, "номер %u не записался в NVS", historyLastIdValue);
  }
  return historyLastIdValue;
}

// Записывает бросок в свободную ячейку. Когда ячейки кончились, пишем поверх самой старой.
// Последний бросок запоминается в памяти в любом случае, даже если флеш недоступна.
// Возвращает true, только если запись перечитана из флеш и совпала с записанной:
// запись в arduino-esp32 ложится при close(), а его ошибок не видно. Не легла —
// ячейка та же (следующий бросок попробует её снова), в historyProblem беда, в info
// histok:false, а после HISTORY_PUSH_FAILS_MAX неудач подряд история выключается.
bool historyPush(const Roll& roll, uint32_t id, uint32_t ts) {
  HistoryRecord r;
  memset(&r, 0, sizeof(r));   // байты, не занятые значениями, входят в сумму и должны быть нулями
  r.id    = id;
  r.ts    = ts;
  r.total = (uint16_t)roll.total;
  r.sides = roll.sides;
  r.count = roll.count;
  r.flags = roll.flags;
  int n = rollValueCount(roll.count, roll.flags);
  for (int i = 0; i < n && i < MAX_DICE; i++) r.values[i] = roll.values[i];
  r.crc = historyCrc(r);
  historyRamLast  = r;
  historyRamValid = true;

  if (!historyReady) return false;
  bool ok = false;
  File f = LittleFS.open(HISTORY_PATH, "r+");
  if (f) {
    ok = f.seek(historyOffset(historyNextSlot)) && f.write((uint8_t*)&r, sizeof(r)) == sizeof(r);
    f.close();
  }
  if (ok) {
    HistoryRecord back;
    File check = LittleFS.open(HISTORY_PATH, FILE_READ);
    ok = check && historyReadSlot(check, historyNextSlot, back) && memcmp(&back, &r, sizeof(r)) == 0;
    if (check) check.close();
  }
  if (!ok) {
    historyPushFails++;
    historyProblem = HISTORY_PUSH_PROBLEM;
    if (historyPushFails >= HISTORY_PUSH_FAILS_MAX) historyDisable("броски несколько раз подряд не легли во флеш");
    return false;
  }

  historyPushFails = 0;
  if (historyProblem == HISTORY_PUSH_PROBLEM) historyProblem = nullptr;
  if (historyCount < HISTORY_FLASH_SIZE) historyCount++;
  historyNextSlot = (uint16_t)((historyNextSlot + 1) % HISTORY_FLASH_SIZE);
  return true;
}

// Итог одной порции выдачи: сколько отдали, каким номером кончили и осталось ли ещё.
struct HistoryPage {
  uint16_t sent;      // сколько записей отдали
  uint32_t lastId;    // номер последней отданной, чтобы приложение попросило продолжение
  bool     more;      // упёрлись в limit, за ним есть ещё записи
  bool     err;       // файл не открылся или чтение оборвалось: выдача неполная
};

// Отдаёт до limit бросков с номером больше afterId, по возрастанию номера.
// Обходим кольцо начиная со свободной ячейки: сразу за ней лежит самый старый бросок,
// поэтому записи идут по порядку и сортировать ничего не нужно. Битые пропускаются.
// Порциями, а не целиком, потому что отправка одного события занимает до 25 мс,
// и всё это время главный цикл стоит: 1500 записей заморозили бы посох почти на минуту.
// Сбой чтения посреди обхода — не «больше ничего нет»: ставит err и пишет ERROR.
HistoryPage historyAfter(uint32_t afterId, uint16_t limit, void (*callback)(const HistoryRecord&)) {
  HistoryPage page = { 0, afterId, false, false };
  if (!historyReady) return page;
  File f = LittleFS.open(HISTORY_PATH, FILE_READ);
  if (!f) {
    page.err = true;
    logLine(LOG_HISTORY, LOG_ERROR, "выдача истории: файл не открылся");
    return page;
  }
  for (uint16_t k = 0; k < HISTORY_FLASH_SIZE; k++) {
    uint16_t slot = (uint16_t)((historyNextSlot + k) % HISTORY_FLASH_SIZE);
    HistoryRecord r;
    if (!historyReadSlot(f, slot, r)) {
      page.err = true;
      logLine(LOG_HISTORY, LOG_ERROR, "выдача истории: чтение оборвалось на ячейке %u", slot);
      break;
    }
    if (r.id == 0 || r.id <= afterId || !historyShowable(r)) continue;
    if (page.sent >= limit) { page.more = true; break; }
    callback(r);
    page.sent++;
    page.lastId = r.id;
  }
  f.close();
  return page;
}

// Складывает в out до max самых свежих целых бросков, свежий первым. Нужна экрану истории
// на посохе (он листает последние HISTORY_SIZE бросков) и «Повтору» после перезапуска.
// Идёт назад от свободной ячейки, битые пропускает, на пустой останавливается:
// кольцо заполняется подряд, за пустой ячейкой бросков нет. Возвращает, сколько положила.
// Сбой чтения — WARN: список на экране выйдет короче, но это не потеря данных.
uint16_t historyLatest(HistoryRecord* out, uint16_t max) {
  if (!historyReady || historyCount == 0) return 0;
  File f = LittleFS.open(HISTORY_PATH, FILE_READ);
  if (!f) {
    logLine(LOG_HISTORY, LOG_WARN, "последние броски: файл не открылся");
    return 0;
  }
  uint16_t n = 0;
  for (uint16_t k = 1; k <= HISTORY_FLASH_SIZE && n < max; k++) {
    uint16_t slot = (uint16_t)((historyNextSlot + HISTORY_FLASH_SIZE - k) % HISTORY_FLASH_SIZE);
    if (!historyReadSlot(f, slot, out[n])) {
      logLine(LOG_HISTORY, LOG_WARN, "последние броски: чтение оборвалось на ячейке %u", slot);
      break;
    }
    if (out[n].id == 0) break;
    if (historyShowable(out[n])) n++;
  }
  f.close();
  return n;
}

// Достаёт самый свежий бросок. Нужна кнопке «Повтор»: она взводит то же, что бросали
// в прошлый раз. Сначала смотрит в память (бросок этого включения), потом во флеш —
// так «Повтор» работает и сразу после включения, и без флеш, пока посох не выключили.
// Возвращает false, если бросков не было или их негде взять.
bool historyLast(HistoryRecord& out) {
  if (historyRamValid) { out = historyRamLast; return true; }
  return historyLatest(&out, 1) == 1;
}

// Бросок из записи истории в том виде, в каком его понимают экран и rollBreakdown.
// Записи сюда попадают уже проверенными, но число костей всё равно ограничиваем:
// по нему идут циклы по values, и выход за край массива хуже неверной цифры.
Roll historyToRoll(const HistoryRecord& rec) {
  Roll r = {};
  r.count = rec.count > MAX_DICE ? MAX_DICE : rec.count;
  r.sides = rec.sides;
  r.total = rec.total;
  r.flags = rec.flags;
  for (int i = 0; i < MAX_DICE; i++) r.values[i] = rec.values[i];
  return r;
}

// Номер последнего броска. Ноль значит, что бросков ещё не было.
uint32_t historyLastId() {
  return historyLastIdValue;
}
