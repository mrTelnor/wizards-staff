#pragma once
#include <Arduino.h>
#include <LittleFS.h>
#include <Preferences.h>
#include "config.h"
#include "dice.h"
#include "log.h"

// История бросков во флеш. Нужна, чтобы броски, сделанные без планшета, не пропадали:
// приложение при подключении догрузит всё, что пропустило.
//
// Устройство: файл постоянного размера, записи фиксированной длины, пишем по кругу.
// Дошли до конца - начинаем сначала поверх самого старого броска.
//
// Номер броска (id) сквозной и не начинается заново при выключении: на нём держится
// опознание бросков в базе планшета. Он лежит в двух местах сразу - в NVS и в самом
// файле - и при старте берётся больший из двух. Так номер переживает потерю любого
// одного из хранилищ: NVS стирается при смене схемы разделов, файл - при форматировании
// LittleFS. Порознь каждое из этих событий уже случалось.

static const char*    HISTORY_PATH   = "/rolls.bin";
static const uint32_t HISTORY_MAGIC  = 0x48525357;   // "WSRH", чтобы не принять чужой файл за свой
static const uint16_t HISTORY_FORMAT = 1;            // версия формата записи, см. комментарий к HistoryRecord

// Шапка файла. Версия формата обязательна: состав записи ещё будет меняться
// (преимущество и помеха в задаче D4), и без версии старый файл прочитается как мусор.
struct HistoryHeader {
  uint32_t magic;
  uint16_t format;
  uint16_t capacity;
};

// Одна запись, ровно 36 байт. Значения и грани по два байта ради костей до d1000.
// Байт flags пока не используется: он заведён заранее под преимущество и помеху,
// потому что менять формат потом дороже, чем зарезервировать байт сразу.
// id == 0 означает пустую ячейку: настоящие номера начинаются с единицы.
struct HistoryRecord {
  uint32_t id;
  uint32_t ts;
  uint16_t values[MAX_DICE];
  uint16_t total;
  uint16_t sides;
  uint8_t  count;
  uint8_t  flags;
};
static_assert(sizeof(HistoryRecord) == 36, "Размер записи истории изменился: подними HISTORY_FORMAT");

uint32_t historyLastIdValue = 0;    // номер последнего броска
uint16_t historyNextSlot    = 0;    // куда писать следующий бросок
uint16_t historyCount       = 0;    // сколько ячеек занято
bool     historyReady       = false;

// Сколько байт занимает ячейка номер slot.
static uint32_t historyOffset(uint16_t slot) {
  return sizeof(HistoryHeader) + (uint32_t)slot * sizeof(HistoryRecord);
}

// Создаёт файл заново: шапка и HISTORY_FLASH_SIZE пустых записей.
// Вызывается, когда файла нет, он повреждён или в нём другая версия формата.
static bool historyCreate() {
  File f = LittleFS.open(HISTORY_PATH, FILE_WRITE);
  if (!f) return false;
  HistoryHeader h = { HISTORY_MAGIC, HISTORY_FORMAT, HISTORY_FLASH_SIZE };
  f.write((uint8_t*)&h, sizeof(h));
  HistoryRecord empty = {};
  for (uint16_t i = 0; i < HISTORY_FLASH_SIZE; i++) f.write((uint8_t*)&empty, sizeof(empty));
  f.close();
  historyNextSlot = 0;
  historyCount    = 0;
  return true;
}

// Готовит историю к работе: поднимает файл, находит место для следующей записи
// и восстанавливает сквозной номер. Вызывать один раз в setup(), после LittleFS.
void historyBegin() {
  bool fresh = true;
  File f = LittleFS.open(HISTORY_PATH, FILE_READ);
  if (f && f.size() == historyOffset(HISTORY_FLASH_SIZE)) {
    HistoryHeader h = {};
    f.read((uint8_t*)&h, sizeof(h));
    if (h.magic == HISTORY_MAGIC && h.format == HISTORY_FORMAT && h.capacity == HISTORY_FLASH_SIZE) {
      // Файл наш и целый: ищем ячейку с самым большим номером, следующая за ней - свободная.
      uint32_t maxId = 0;
      uint16_t maxSlot = 0;
      for (uint16_t i = 0; i < HISTORY_FLASH_SIZE; i++) {
        HistoryRecord r = {};
        f.read((uint8_t*)&r, sizeof(r));
        if (r.id == 0) continue;
        historyCount++;
        if (r.id > maxId) { maxId = r.id; maxSlot = i; }
      }
      historyLastIdValue = maxId;
      historyNextSlot    = maxId ? (uint16_t)((maxSlot + 1) % HISTORY_FLASH_SIZE) : 0;
      fresh = false;
    }
  }
  if (f) f.close();

  if (fresh) {
    logLine(LOG_HISTORY, LOG_INFO, "файла нет или он другого формата, создаю заново");
    if (!historyCreate()) {
      logLine(LOG_HISTORY, LOG_ERROR, "файл не создан, броски сохраняться не будут");
      return;
    }
  }

  // Номер из NVS: он мог уйти вперёд, если файл потеряли, а настройки остались.
  Preferences prefs;
  prefs.begin("staff", false);
  uint32_t saved = prefs.getUInt("rollid", 0);
  if (saved > historyLastIdValue) historyLastIdValue = saved;
  if (saved != historyLastIdValue) prefs.putUInt("rollid", historyLastIdValue);
  prefs.end();

  historyReady = true;
  logLine(LOG_HISTORY, LOG_INFO, "%u из %u записей, последний номер %u",
          historyCount, (unsigned)HISTORY_FLASH_SIZE, historyLastIdValue);
}

// Выдаёт номер для нового броска и сразу запоминает его во флеш.
// Про износ: NVS раскидывает записи по странице, стирание выходит раз на сотню бросков,
// ресурс порядка ста тысяч стираний - это десятки тысяч игровых вечеров.
uint32_t historyNextId() {
  historyLastIdValue++;
  Preferences prefs;
  prefs.begin("staff", false);
  prefs.putUInt("rollid", historyLastIdValue);
  prefs.end();
  return historyLastIdValue;
}

// Записывает бросок в свободную ячейку. Когда ячейки кончились, пишем поверх самой старой.
bool historyPush(const Roll& roll, uint32_t id, uint32_t ts, uint8_t flags) {
  if (!historyReady) return false;
  HistoryRecord r = {};
  r.id    = id;
  r.ts    = ts;
  r.total = (uint16_t)roll.total;
  r.sides = roll.sides;
  r.count = roll.count;
  r.flags = flags;
  for (int i = 0; i < roll.count && i < MAX_DICE; i++) r.values[i] = roll.values[i];

  File f = LittleFS.open(HISTORY_PATH, "r+");
  if (!f) return false;
  f.seek(historyOffset(historyNextSlot));
  size_t written = f.write((uint8_t*)&r, sizeof(r));
  f.close();
  if (written != sizeof(r)) return false;

  if (historyCount < HISTORY_FLASH_SIZE) historyCount++;
  historyNextSlot = (uint16_t)((historyNextSlot + 1) % HISTORY_FLASH_SIZE);
  return true;
}

// Итог одной порции выдачи: сколько отдали, каким номером кончили и осталось ли ещё.
struct HistoryPage {
  uint16_t sent;      // сколько записей отдали
  uint32_t lastId;    // номер последней отданной, чтобы приложение попросило продолжение
  bool     more;      // упёрлись в limit, за ним есть ещё записи
};

// Отдаёт до limit бросков с номером больше afterId, по возрастанию номера.
// Обходим кольцо начиная со свободной ячейки: сразу за ней лежит самый старый бросок,
// поэтому записи идут по порядку и сортировать ничего не нужно.
// Порциями, а не целиком, потому что отправка одного события занимает до 25 мс,
// и всё это время главный цикл стоит: 1500 записей заморозили бы посох почти на минуту.
HistoryPage historyAfter(uint32_t afterId, uint16_t limit, void (*callback)(const HistoryRecord&)) {
  HistoryPage page = { 0, afterId, false };
  if (!historyReady) return page;
  File f = LittleFS.open(HISTORY_PATH, FILE_READ);
  if (!f) return page;
  for (uint16_t k = 0; k < HISTORY_FLASH_SIZE; k++) {
    uint16_t slot = (uint16_t)((historyNextSlot + k) % HISTORY_FLASH_SIZE);
    HistoryRecord r = {};
    f.seek(historyOffset(slot));
    if (f.read((uint8_t*)&r, sizeof(r)) != sizeof(r)) break;
    if (r.id == 0 || r.id <= afterId) continue;
    if (page.sent >= limit) { page.more = true; break; }
    callback(r);
    page.sent++;
    page.lastId = r.id;
  }
  f.close();
  return page;
}

// Номер последнего броска. Ноль значит, что бросков ещё не было.
uint32_t historyLastId() {
  return historyLastIdValue;
}
