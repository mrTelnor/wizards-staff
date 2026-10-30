#pragma once
#include <Arduino.h>
#include <LittleFS.h>
#include <Preferences.h>
#include <esp_partition.h>
#include "config.h"
#include "log.h"

// Раздел под файлы (LittleFS): монтирование и осознанное форматирование.
//
// Главное правило: данные не стираются молча. До 0.22.0 любая ошибка монтирования
// кончалась форматированием, а неудачное монтирование не доказывает, что данных там нет:
// это мог быть разовый сбой, и вместе с ним уходила вся история бросков.
//
// Сам посох форматирует раздел, только когда сходится всё сразу: в NVS нет отметки
// «раздел уже работал» (ключ fsready), номер последнего броска в NVS ноль, и первые
// блоки раздела чистые (одни 0xFF, как после стирания). Так бывает на плате после
// Erase All Flash и на новой плате с чистой флеш (G54: ошибка -84 при этом — норма).
// Хоть одно не сошлось или не прочиталось — это сбой: не форматируем, посох работает
// без истории во флеш, отформатировать можно только командой fsformat с планшета.
//
// Смена схемы разделов в Arduino IDE. Обычно NVS лежит на том же месте и переживает
// заливку вместе с отметкой и номером, а на месте нового раздела лежат старые байты.
// Тогда раздел не монтируется, и посох честно считает это сбоем и ждёт fsformat.
// Новая плата с заводской прошивкой тоже может оказаться не чистой — тот же путь.
// Это сознательно: лишний шаг руками дешевле, чем стёртая по ошибке история.

bool fsMounted        = false;   // раздел смонтирован и с ним можно работать
bool fsWasReadyAtBoot = false;   // стояла ли отметка «раздел работал» до этого включения
bool fsJustFormatted  = false;   // раздел только что отформатирован: пустой файл истории — норма

// Ответ на вопрос «есть ли файл»: да, нет или узнать не удалось.
enum FsEntry { FS_ENTRY_YES, FS_ENTRY_NO, FS_ENTRY_UNKNOWN };

// Есть ли файл path в корне раздела. Спрашивает два источника, и ответ «нет» или «да»
// даёт, только когда они согласны; иначе FS_ENTRY_UNKNOWN, и файл не трогают.
// Зачем так: ни одному источнику по отдельности верить нельзя, а от «нет» зависит,
// заведётся ли новый файл поверх (rename в LittleFS молча заменяет существующий).
// - exists()/open() в arduino-esp32 — попытка открыть, разовый сбой выглядит как «нет»;
// - getNextFileName() в ядре 3.3.12 отдаёт "" и в конце каталога, и при ошибке readdir,
//   так что сбой обхода тоже выглядит как «нет».
FsEntry fsHasFile(const char* path) {
  if (!fsMounted) return FS_ENTRY_UNKNOWN;
  File dir = LittleFS.open("/");
  if (!dir || !dir.isDirectory()) {
    if (dir) dir.close();
    return FS_ENTRY_UNKNOWN;
  }
  bool listed = false;
  for (String name = dir.getNextFileName(); name.length(); name = dir.getNextFileName()) {
    if (name == path) { listed = true; break; }
  }
  dir.close();
  File f = LittleFS.open(path, FILE_READ);
  bool opens = (bool)f;
  if (f) f.close();
  if (listed && opens)   return FS_ENTRY_YES;
  if (!listed && !opens) return FS_ENTRY_NO;
  return FS_ENTRY_UNKNOWN;
}

// Есть ли в NVS отметка, что раздел уже когда-то монтировался. Не открылся NVS —
// отвечаем «да»: незнание не повод считать раздел пустым.
static bool fsWasReady() {
  Preferences prefs;
  if (!prefs.begin("staff", false)) return true;
  bool ready = prefs.getBool("fsready", false);
  prefs.end();
  return ready;
}

// Ноль ли номер последнего броска в NVS (ключ rollid, его ведёт history.h).
// Не открылся NVS — «не ноль», по той же причине, что в fsWasReady.
static bool fsRollIdZero() {
  Preferences prefs;
  if (!prefs.begin("staff", false)) return false;
  uint32_t id = prefs.getUInt("rollid", 0);
  prefs.end();
  return id == 0;
}

// Ставит отметку «раздел уже работал». Пишет только если её ещё нет,
// чтобы не трогать NVS при каждом включении. Не записалась — ERROR: тогда
// будущий сбой монтирования может принять за первый запуск только чистый раздел.
static void fsMarkReady() {
  Preferences prefs;
  if (!prefs.begin("staff", false)) {
    logLine(LOG_FS, LOG_ERROR, "NVS не открылся, отметка «раздел работает» не записана");
    return;
  }
  bool ok = prefs.getBool("fsready", false) || prefs.putBool("fsready", true) == 1;
  prefs.end();
  if (!ok) logLine(LOG_FS, LOG_ERROR, "отметка «раздел работает» не записалась в NVS");
}

// Чисты ли первые FS_BLANK_CHECK_BYTES раздела под файлы: одни 0xFF, как после стирания.
// Читаем напрямую из флеш, мимо LittleFS. Раздел не найден или не читается — «не чист».
static bool fsPartitionBlank() {
  const esp_partition_t* part = esp_partition_find_first(ESP_PARTITION_TYPE_DATA,
                                                         ESP_PARTITION_SUBTYPE_DATA_SPIFFS, nullptr);
  if (!part) return false;
  uint8_t buf[256];
  uint32_t total = FS_BLANK_CHECK_BYTES < part->size ? FS_BLANK_CHECK_BYTES : part->size;
  for (uint32_t at = 0; at < total; at += sizeof(buf)) {
    if (esp_partition_read(part, at, buf, sizeof(buf)) != ESP_OK) return false;
    for (size_t i = 0; i < sizeof(buf); i++) if (buf[i] != 0xFF) return false;
  }
  return true;
}

// Одна попытка монтирования без форматирования. В проверочном режиме
// FS_TEST_MOUNT_FAIL всегда «не получилось», и раздел при этом не трогается вовсе.
static bool fsTryMount() {
  if (FS_TEST_MOUNT_FAIL) return false;
  return LittleFS.begin(false);
}

// Печатает размер раздела. Нужен, чтобы видеть, хватает ли места истории.
static void fsReportSize() {
  logLine(LOG_FS, LOG_INFO, "%u КБ всего, %u КБ занято, %u КБ свободно",
          (unsigned)(LittleFS.totalBytes() / 1024),
          (unsigned)(LittleFS.usedBytes() / 1024),
          (unsigned)((LittleFS.totalBytes() - LittleFS.usedBytes()) / 1024));
}

// Готовит раздел к работе при старте. Вызывать в setup() до historyBegin().
// Первый запуск (см. начало файла) — форматирует, всё остальное — не форматирует.
void fsBegin() {
  if (FS_TEST_MOUNT_FAIL) {
    logLine(LOG_FS, LOG_WARN, "ПРОВЕРОЧНЫЙ РЕЖИМ: сбой монтирования изображается, раздел не трогаю");
    logLine(LOG_FS, LOG_WARN, "после проверки FS_TEST_MOUNT_FAIL = false в config.h");
  }

  bool ok = fsTryMount();
  if (!ok) {
    // Вторая попытка: разовый сбой чтения флеш не повод думать, что данных нет.
    LittleFS.end();
    delay(FS_MOUNT_RETRY_MS);
    ok = fsTryMount();
  }

  if (ok) {
    fsMounted = true;
    fsWasReadyAtBoot = fsWasReady();
    fsMarkReady();   // посохи с прошивкой до 0.22.0 получают отметку при первом же старте
    fsReportSize();
    return;
  }

  bool wasReady = fsWasReady();
  fsWasReadyAtBoot = wasReady;
  bool idZero   = fsRollIdZero();
  bool blank    = fsPartitionBlank();
  if (FS_TEST_MOUNT_FAIL || wasReady || !idZero || !blank) {
    logLine(LOG_FS, LOG_ERROR, "раздел не смонтирован и НЕ форматирую (отметка %s, номер %s, раздел %s)",
            wasReady ? "есть" : "нет", idZero ? "ноль" : "не ноль", blank ? "чист" : "не чист");
    logLine(LOG_FS, LOG_ERROR, "броски идут и уходят на планшет, но во флеш не пишутся");
    logLine(LOG_FS, LOG_ERROR, "стереть раздел: команда fsformat с confirm \"%s\" с планшета",
            FS_FORMAT_CONFIRM);
    return;
  }

  // Всё сошлось: раздел ни разу не работал и он чистый, данных там нет.
  logLine(LOG_FS, LOG_INFO, "раздел чистый и ещё ни разу не работал, форматирую (первый запуск)");
  if (!LittleFS.format() || !LittleFS.begin(false)) {
    logLine(LOG_FS, LOG_ERROR, "раздел не поднялся даже после форматирования");
    logLine(LOG_FS, LOG_ERROR, "проверь Tools -> Partition Scheme: нужен Minimal SPIFFS");
    return;
  }
  logLine(LOG_FS, LOG_INFO, "отформатирован успешно");
  fsMounted = true;
  fsJustFormatted = true;
  fsMarkReady();
  fsReportSize();
}

// Осознанное форматирование по команде fsformat. Стирает раздел целиком.
// Разрешено в двух случаях:
// - раздел не смонтирован и не монтируется и сейчас (сбой, смена схемы разделов);
// - раздел смонтирован, но история выключена из-за непригодного файла (unusableFile):
//   файл не читается или он чужой, а отложить его некуда. Без этого выхода из такого
//   тупика не было бы вовсе, кроме Erase All Flash, который стирает ещё PIN и номер посоха.
// Рабочий раздел с работающей историей так не стереть. В проверочных режимах запрещено
// совсем — там данные на самом деле целы. Возвращает текст отказа или nullptr.
const char* fsFormatConfirmed(bool unusableFile) {
  if (FS_TEST_MOUNT_FAIL || HISTORY_TEST_BAD_ID) return "проверочный режим: данные на самом деле целы, форматирование запрещено";
  if (fsMounted && !unusableFile) return "раздел работает, форматировать незачем: история на месте";
  if (fsMounted) {
    logLine(LOG_FS, LOG_WARN, "форматирую рабочий раздел: файл истории непригоден, стираю всё");
    LittleFS.end();
    fsMounted = false;
    if (!LittleFS.format() || !LittleFS.begin(false)) {
      logLine(LOG_FS, LOG_ERROR, "раздел не поднялся после форматирования");
      return "раздел не поднялся после форматирования";
    }
    fsMounted = true;
    fsJustFormatted = true;
    fsMarkReady();
    logLine(LOG_FS, LOG_INFO, "отформатирован по команде");
    fsReportSize();
    return nullptr;
  }
  // Последняя попытка перед стиранием: вдруг сбой был разовым и раздел уже читается.
  // Тогда отказ, а историю поднимает вызывающий (см. команду fsformat).
  LittleFS.end();
  if (LittleFS.begin(false)) {
    fsMounted = true;
    fsMarkReady();
    logLine(LOG_FS, LOG_INFO, "перед форматированием раздел смонтировался сам, ничего не стираю");
    fsReportSize();
    return "раздел смонтировался, форматировать незачем: история на месте";
  }
  logLine(LOG_FS, LOG_WARN, "форматирую раздел по команде с планшета, история во флеш стирается");
  LittleFS.end();
  if (!LittleFS.format() || !LittleFS.begin(false)) {
    logLine(LOG_FS, LOG_ERROR, "раздел не поднялся и после форматирования");
    return "раздел не поднялся и после форматирования";
  }
  fsMounted = true;
  fsJustFormatted = true;
  fsMarkReady();
  logLine(LOG_FS, LOG_INFO, "отформатирован по команде");
  fsReportSize();
  return nullptr;
}
