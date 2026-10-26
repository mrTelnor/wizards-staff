#pragma once
#include <Arduino.h>
#include "config.h"

// Пищалка: электромагнитный зуммер 12085 через транзистор S8050 на ножке PIN_BUZZER.
// Зуммер пассивный, сам не пищит: звук даёт ШИМ на нужной частоте.
//
// Главное правило: в тишине на ножке НОЛЬ. Единица открывает транзистор, и через
// катушку постоянно идёт около 120 мА: зуммер греется и сажает аккумулятор.
// Поэтому управляем ножкой сами через LEDC, а не через tone(): после tone() состояние
// ножки зависит от версии пакета плат, а ledcWrite(…, 0) всегда даёт ноль.
//
// На этой же ножке у SuperMini синий светодиод платы, он горит при нуле. Значит,
// в тишине он горит постоянно, а во время звука мерцает. В закрытой рукояти не видно.
//
// Все звуки блокирующие: самый длинный идёт около полсекунды и звучит после броска,
// когда датчик удара всё равно не слушается (STRIKE_COOLDOWN_MS).

bool soundEnabled = true;   // выключит кнопка «Тихо», когда до неё дойдёт очередь

// Около этой частоты зуммер 12085 звучит громче всего.
const uint32_t SOUND_TICK_HZ  = 2700;
const uint32_t SOUND_CLICK_HZ = 4000;

// Ставит ножку пищалки в ноль и готовит ШИМ. Вызывать самой первой строкой setup():
// пока ножка не задана, она висит в воздухе, и транзистор может приоткрыться.
void soundBegin() {
  pinMode(PIN_BUZZER, OUTPUT);
  digitalWrite(PIN_BUZZER, LOW);
  ledcAttach(PIN_BUZZER, SOUND_TICK_HZ, 10);
  ledcWrite(PIN_BUZZER, 0);
}

// Одна нота: частота в герцах, длительность в миллисекундах. После неё ножка снова в нуле.
void soundNote(uint32_t hz, unsigned long ms) {
  if (!soundEnabled) return;
  ledcWriteTone(PIN_BUZZER, hz);   // заполнение 50 %: громче всего
  delay(ms);
  ledcWrite(PIN_BUZZER, 0);
}

// Пауза между нотами. Отдельно, чтобы при выключенном звуке не ждать впустую.
void soundRest(unsigned long ms) {
  if (soundEnabled) delay(ms);
}

// Короткий сигнал при включении: посох жив и пищалка подключена.
void soundStartup() {
  soundNote(2093, 60);
  soundRest(30);
  soundNote(3136, 80);
}

// Тихий щелчок на нажатие кнопки: подтверждение, что нажатие засчитано.
void soundClick() {
  soundNote(SOUND_CLICK_HZ, 2);
}

// Тик барабана: на каждой смене числа во время броска.
void soundTick() {
  soundNote(SOUND_TICK_HZ, 3);
}

// Аккорд после обычного броска: три ноты вверх.
void soundChord() {
  soundNote(2093, 70);  soundRest(20);
  soundNote(2637, 70);  soundRest(20);
  soundNote(3136, 140);
}

// Фанфара на 20 по d20.
void soundCrit() {
  soundNote(2093, 70);  soundRest(20);
  soundNote(2637, 70);  soundRest(20);
  soundNote(3136, 70);  soundRest(20);
  soundNote(4186, 300);
}

// Грустный звук на 1 по d20: две ноты вниз.
void soundFail() {
  soundNote(1319, 200);  soundRest(40);
  soundNote(880, 400);
}
