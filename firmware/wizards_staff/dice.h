#pragma once
#include <Arduino.h>
#include <Preferences.h>
#include "config.h"
#include "log.h"

// Бросок костей. Случайные числа даёт аппаратный генератор ESP32, поэтому бросок честный.

// Какая кость назначена каждой из восьми кнопок. Лежит во флеш, потому что назначение
// меняется с планшета командой map и должно переживать выключение. Номер в таблице -
// номер входа расширителя костей: diceSides[0] висит на P0, самой левой кнопке.
uint16_t diceSides[8];

// Собирает список костей строкой вида "d2 d4 d6 d8 d10 d12 d20 d100".
// Вынесено отдельно, потому что нужно в трёх местах: при старте, в ответе на info
// и после переназначения кнопок командой map.
void diceList(char* out, size_t len) {
  out[0] = 0;
  for (int i = 0; i < 8; i++) {
    char part[10];
    snprintf(part, sizeof(part), i ? " d%u" : "d%u", diceSides[i]);
    strlcat(out, part, len);
  }
}

// Приводит таблицу костей к порядку: сортирует по возрастанию, чтобы кнопки шли слева
// направо от меньшей кости к большей и таблица не зависела от того, в каком порядке её
// прислали. Возвращает 0, если всё хорошо, иначе грань, которая встретилась дважды:
// две одинаковые кости на разных кнопках смысла не имеют, а выглядят как опечатка.
uint16_t diceNormalize(uint16_t* sides) {
  // Сортировка вставками: значений восемь, городить что-то сложнее незачем.
  for (int i = 1; i < 8; i++) {
    uint16_t v = sides[i];
    int j = i - 1;
    while (j >= 0 && sides[j] > v) { sides[j + 1] = sides[j]; j--; }
    sides[j + 1] = v;
  }
  // После сортировки одинаковые стоят рядом, и хватает одного прохода.
  for (int i = 1; i < 8; i++) {
    if (sides[i] == sides[i - 1]) return sides[i];
  }
  return 0;
}

// Читает таблицу костей из флеш. Если её там нет или она битая, берёт значения
// по умолчанию из config.h. Вызывать один раз в setup().
void diceLoad() {
  Preferences prefs;
  prefs.begin("staff", false);
  size_t got = prefs.getBytes("dice", diceSides, sizeof(diceSides));
  prefs.end();

  // Два разных случая, и путать их не надо: пусто это обычное дело до первой команды map,
  // а вот испорченная таблица означает порчу данных во флеш и должна бросаться в глаза.
  bool empty = (got == 0);
  bool ok    = (got == sizeof(diceSides));
  uint16_t twice = 0;
  if (ok) {
    for (int i = 0; i < 8; i++) {
      if (diceSides[i] < 2 || diceSides[i] > DICE_SIDES_MAX) { ok = false; break; }
    }
  }
  // Повторы во флеш могли остаться от прошивки, которая их ещё не запрещала.
  // Чиним сами, а не молча живём с негодной таблицей.
  if (ok) {
    twice = diceNormalize(diceSides);
    if (twice) ok = false;
  }
  if (!ok) {
    memcpy(diceSides, DICE_SIDES_DEFAULT, sizeof(diceSides));
    if (empty)      logLine(LOG_DICE, LOG_INFO, "во флеш ничего нет, это первый запуск, беру значения по умолчанию");
    else if (twice) logLine(LOG_DICE, LOG_WARN, "во флеш кость d%u дважды, беру значения по умолчанию", twice);
    else            logLine(LOG_DICE, LOG_WARN, "во флеш мусор, таблица не читается, беру значения по умолчанию");
  }
  char list[64];
  diceList(list, sizeof(list));
  logLine(LOG_DICE, LOG_INFO, "кости по кнопкам: %s", list);
}

// Сохраняет таблицу костей во флеш. Вызывается из обработчика команды map.
void diceSave() {
  Preferences prefs;
  prefs.begin("staff", false);
  prefs.putBytes("dice", diceSides, sizeof(diceSides));
  prefs.end();
}

// Один бросок: сколько костей, каких, что выпало на каждой, сумма.
// Грани и значения по два байта, а не по одному: протокол разрешает кости до d1000
// (в ТЗ, раздел 7а, это «например d3 или d1000»), а в байт влезает только 255.
// До 2026-09-18 поля были однобайтовыми, и запрос d1000 молча превращался в d232.
// Удача и неудача (SPEC 2.5): одна кость бросается дважды, в зачёт идёт лучшая или худшая.
// Эти же числа лежат в байте flags записи истории, на планшет уходят полем adv: 1 и -1.
const uint8_t ROLL_FORTUNE    = 1;
const uint8_t ROLL_MISFORTUNE = 2;

// flags — ROLL_FORTUNE, ROLL_MISFORTUNE или 0. У удачи и неудачи count остаётся 1:
// кость одна, просто брошена дважды. Оба броска лежат в values[0] и values[1],
// а total — выбранный из них, а не их сумма.
struct Roll {
  uint8_t  count;
  uint16_t sides;
  uint16_t values[MAX_DICE];
  int      total;
  uint8_t  flags;
};

// Сколько чисел лежит в values: у удачи и неудачи их два при одной кости.
int rollValueCount(uint8_t count, uint8_t flags) {
  return flags ? 2 : count;
}

// Бросает count костей по sides граней. count ограничен MAX_DICE.
// Удача и неудача бывают только у одной кости, для нескольких flags не действует.
Roll rollDice(uint8_t count, uint16_t sides, uint8_t flags = 0) {
  Roll r;
  r.count = count > MAX_DICE ? MAX_DICE : count;
  r.sides = sides;
  r.flags = r.count == 1 ? flags : 0;
  if (r.flags) {
    r.values[0] = random(1, sides + 1);
    r.values[1] = random(1, sides + 1);
    bool firstWins = r.flags == ROLL_FORTUNE ? r.values[0] >= r.values[1]
                                             : r.values[0] <= r.values[1];
    r.total = firstWins ? r.values[0] : r.values[1];
    return r;
  }
  r.total = 0;
  for (int i = 0; i < r.count; i++) {
    r.values[i] = random(1, sides + 1);   // от 1 до sides включительно
    r.total += r.values[i];
  }
  return r;
}

// Имя режима для журнала: «удача», «неудача» или пустая строка у обычного броска.
const char* rollModeName(uint8_t flags) {
  return flags == ROLL_FORTUNE ? "удача" : flags == ROLL_MISFORTUNE ? "неудача" : "";
}

// Собирает строку вида "2+1+4" из значений броска, для Serial и экрана.
// У удачи и неудачи оба броска через запятую, «14, 7»: так их не спутать со слагаемыми.
void rollBreakdown(const Roll& r, char* out, size_t len) {
  if (r.flags) {
    snprintf(out, len, "%u, %u", r.values[0], r.values[1]);
    return;
  }
  out[0] = 0;
  for (int i = 0; i < r.count; i++) {
    char part[8];
    snprintf(part, sizeof(part), i == 0 ? "%d" : "+%d", r.values[i]);
    strlcat(out, part, len);
  }
}

// Самопроверка генератора: 6000 бросков d6, печатает, сколько раз выпала каждая грань.
// Ожидаем около 1000 на каждую грань, разброс плюс-минус сотня это нормально.
void diceSelfTest() {
  int hits[7] = { 0 };
  for (int i = 0; i < 6000; i++) hits[rollDice(1, 6).values[0]]++;
  logLine(LOG_DICE, LOG_INFO, "самопроверка d6 x 6000: %d %d %d %d %d %d (ждём около 1000 на грань)",
          hits[1], hits[2], hits[3], hits[4], hits[5], hits[6]);
}
