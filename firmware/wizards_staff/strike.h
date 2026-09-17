#pragma once
#include "config.h"

// Датчик удара SW-420. Внутри пружинка в трубке: при встряске она касается стенки и выход
// модуля дёргается туда-сюда. Направление нам не важно, считаем переключения уровня.
//
// Фильтр силы удара: настоящий удар об пол даёт пачку из многих переключений за короткое время,
// а перемещение посоха или касание стола одно-два. Первое переключение открывает окно
// STRIKE_WINDOW_MS, внутри окна переключения считаются, по его концу пачка либо засчитывается
// как удар (переключений не меньше STRIKE_MIN_EDGES), либо отбрасывается. Число переключений
// печатается в Serial, чтобы подбирать порог под конкретный посох.

int           strikeLastLevel = 0;   // уровень выхода датчика при прошлом опросе
unsigned long strikeWindowAt  = 0;   // когда открылось текущее окно, 0 если окна нет
int           strikeEdges     = 0;   // переключений в текущем окне

void strikeBegin() {
  pinMode(PIN_STRIKE, INPUT);
  strikeLastLevel = digitalRead(PIN_STRIKE);
}

// Забывает накопленные переключения. Вызывать при взводе и после броска,
// чтобы дрожание от предыдущего удара не пошло новым.
void strikeReset() {
  strikeLastLevel = digitalRead(PIN_STRIKE);
  strikeWindowAt  = 0;
  strikeEdges     = 0;
}

// Опрашивать в каждом обороте loop(). Возвращает true один раз на пачку, прошедшую порог.
bool strikeDetected() {
  unsigned long now = millis();

  int level = digitalRead(PIN_STRIKE);
  if (level != strikeLastLevel) {
    strikeLastLevel = level;
    if (strikeWindowAt == 0) strikeWindowAt = now;   // первое переключение открывает окно
    strikeEdges++;
  }

  if (strikeWindowAt == 0 || now - strikeWindowAt < STRIKE_WINDOW_MS) return false;

  // Окно закрылось: решаем, удар это или дрожание.
  int edges = strikeEdges;
  strikeWindowAt = 0;
  strikeEdges = 0;
  bool isStrike = edges >= STRIKE_MIN_EDGES;
  Serial.printf("Датчик: %d переключений за %lu мс, %s\n", edges, STRIKE_WINDOW_MS,
                isStrike ? "УДАР" : "не удар");
  return isStrike;
}
