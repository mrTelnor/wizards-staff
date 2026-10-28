#pragma once
#include <Arduino.h>
#include "config.h"
#include "lights.h"

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
// Все звуки блокирующие: самый длинный, грустный тромбон, идёт около 2 с и звучит после
// броска, когда датчик удара всё равно не слушается (STRIKE_COOLDOWN_MS, 5 с). Ждут они через
// ringWait, а не delay: звук итога и фанфары звучат под вспышку кольца, и она не должна замирать.

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
  ringWait(ms);
  ledcWrite(PIN_BUZZER, 0);
}

// Пауза между нотами. Отдельно, чтобы при выключенном звуке не ждать впустую.
void soundRest(unsigned long ms) {
  if (soundEnabled) ringWait(ms);
}

// Шаг, с которым меняются частота и громкость в переливах ниже.
const unsigned long SOUND_STEP_MS = 3;

// Громкость в переливах: заполнение ШИМ от 0 (тишина) до 511 (половина периода, громче всего).
// У электромагнитного зуммера разница не так велика, как у динамика, но затихание слышно.
const uint32_t SOUND_VOL_MAX = 511;

// Плавно ведёт звук от одной частоты и громкости к другой за ms миллисекунд.
// jitter — случайный разброс частоты в процентах на каждом шаге: при 20–30 % тон
// рассыпается в шипение, так звучит взмах клинка.
void soundSweep(uint32_t fromHz, uint32_t toHz, unsigned long ms,
                uint32_t fromVol, uint32_t toVol, int jitter = 0) {
  if (!soundEnabled) return;
  for (unsigned long t = 0; t < ms; t += SOUND_STEP_MS) {
    long hz  = fromHz  + ((long)toHz  - (long)fromHz)  * (long)t / (long)ms;
    long vol = fromVol + ((long)toVol - (long)fromVol) * (long)t / (long)ms;
    if (jitter) hz = hz * random(100 - jitter, 101 + jitter) / 100;
    ledcWriteTone(PIN_BUZZER, hz);   // ставит частоту и заполнение 50 %...
    ledcWrite(PIN_BUZZER, vol);      // ...а громкость задаём сами
    ringWait(SOUND_STEP_MS);
  }
  ledcWrite(PIN_BUZZER, 0);
}

// Дрожащий тон: частота качается вокруг hz на depth герц rate раз в секунду, громкость
// тем временем идёт от fromVol к toVol. Медленное качание — вибрато тромбона,
// быстрое — мерцание отблеска.
void soundVibrato(uint32_t hz, uint32_t depth, float rate, unsigned long ms,
                  uint32_t fromVol, uint32_t toVol) {
  if (!soundEnabled) return;
  for (unsigned long t = 0; t < ms; t += SOUND_STEP_MS) {
    long f   = hz + depth * sinf(2 * PI * rate * t / 1000.0f);
    long vol = fromVol + ((long)toVol - (long)fromVol) * (long)t / (long)ms;
    ledcWriteTone(PIN_BUZZER, f);
    ledcWrite(PIN_BUZZER, vol);
    ringWait(SOUND_STEP_MS);
  }
  ledcWrite(PIN_BUZZER, 0);
}

// Писк при включении, как у компьютера, прошедшего самопроверку (POST): один короткий
// ровный сигнал около 1 кГц. Пищалка на этой частоте тише, чем на 2,7 кГц, зато звук узнаётся.
void soundStartup() {
  soundNote(1000, 200);
}

// Тихий щелчок на нажатие кнопки: подтверждение, что нажатие засчитано.
void soundClick() {
  soundNote(SOUND_CLICK_HZ, 2);
}

// Тик барабана: на каждой смене числа во время броска.
void soundTick() {
  soundNote(SOUND_TICK_HZ, 3);
}

// «Пуньк» после обычного броска: кубик стукнул и остановился. Тон за долю секунды падает
// вниз и гаснет, около 0,13 с. Совсем низко пищалка не может: ниже 1 кГц она почти
// не слышна, поэтому удар начинается с 1,6 кГц и только хвост уходит к 500 Гц.
void soundChord() {
  soundSweep(1600, 700, 60, SOUND_VOL_MAX, 250);   // «пу»: удар, тон резко падает
  soundSweep(700, 500, 70, 250, 0);                // «ньк»: короткий глухой хвост
}

// «Вжух-блик» на 20 по d20, около 0,85 с: резкий взмах клинка, переходящий в сияющий отблеск.
void soundCrit() {
  // Вжух: шипящий взмах снизу вверх, громкость нарастает к концу удара.
  soundSweep(1200, 4500, 150, 120, SOUND_VOL_MAX, 25);
  soundRest(50);
  // Блик: три звонкие капли вверх (до, ми, соль восьмой октавы)...
  soundNote(4186, 30);  soundRest(20);
  soundNote(5274, 30);  soundRest(20);
  soundNote(6272, 50);
  // ...и отблеск: высокий тон быстро мерцает и тает.
  soundVibrato(5274, 300, 30, 500, SOUND_VOL_MAX, 10);
}

// Одно «ва» грустного тромбона: звук раскрывается и чуть сползает вниз, как под сурдиной.
void soundWah(uint32_t hz, unsigned long ms) {
  soundSweep(hz * 102 / 100, hz, ms * 2 / 5, 60, SOUND_VOL_MAX);
  soundSweep(hz, hz * 99 / 100, ms * 3 / 5, SOUND_VOL_MAX, 150);
}

// Грустный тромбон на 1 по d20, около 2,2 с: «ва-ва-ва-ваааа», четыре ноты вниз по полутонам,
// последняя длинная, дрожит и затихает. На три октавы выше настоящего тромбона:
// ниже 1 кГц наша пищалка почти не слышна.
void soundFail() {
  const uint32_t wah[3] = { 2349, 2217, 2093 };   // ре, до-диез, до седьмой октавы
  for (int i = 0; i < 3; i++) {
    soundWah(wah[i], 330);
    soundRest(40);
  }
  // Последняя нота, си шестой октавы: громкость нарастает, потом долгое вибрато.
  soundSweep(1976 * 102 / 100, 1976, 80, 60, SOUND_VOL_MAX);
  soundVibrato(1976, 40, 6, 1000, SOUND_VOL_MAX, 30);
}
