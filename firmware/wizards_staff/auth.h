#pragma once
#include <Arduino.h>
#include <Preferences.h>
#include "config.h"
#include "log.h"

// PIN посоха и защита от подбора. Правила целиком - в docs/ПЛАН-APP.md,
// разделы «Авторизация» и «PIN». Коротко: без PIN посох не отвечает ни на что,
// кроме сокращённых сведений о себе, и сам разрывает связь через AUTH_TIMEOUT_MS.
//
// PIN хранится СТРОКОЙ, а не числом. Числом «0042» превратилось бы в 42 и перестало
// совпадать с тем, что набрали на планшете.
//
// secrets.h в репозиторий не входит: попавший в коммит секрет остаётся в истории
// навсегда. Если файла нет, скетч всё равно собирается и посох работает без защиты -
// это лучше, чем непонятная ошибка компиляции у того, кто повторяет проект по описанию.
#if __has_include("secrets.h")
  #include "secrets.h"
#else
  #define STAFF_PIN ""
#endif

char     authPin[PIN_LENGTH + 1] = "";   // пустая строка значит «защиты нет»
int      authMisses       = 0;           // промахов подряд, только в оперативной памяти
uint32_t authBlockedUntil = 0;           // millis(), до которого попытки не принимаем
bool     authBlocked      = false;       // пауза вообще назначена

// Годится ли строка в PIN: ровно PIN_LENGTH цифр и ничего больше.
static bool authValidPin(const char* pin) {
  if (!pin) return false;
  if (strlen(pin) != (size_t)PIN_LENGTH) return false;
  for (int i = 0; i < PIN_LENGTH; i++) {
    if (!isdigit((unsigned char)pin[i])) return false;
  }
  return true;
}

// Включена ли защита. Пустой PIN значит, что посох отвечает всем подряд.
bool authIsSet() {
  return authPin[0] != 0;
}

// Достаёт PIN при старте. Флеш главнее secrets.h: после того как PIN однажды лёг
// в NVS, правка файла ничего не меняет. Иначе вышла бы ловушка «поменял в файле,
// залил, а PIN старый». Сменить PIN можно только командой pin с планшета.
// Вызывать один раз в setup().
void authBegin() {
  Preferences prefs;
  prefs.begin("staff", false);
  String saved = prefs.getString("pin", "");
  if (saved.length()) {
    strlcpy(authPin, saved.c_str(), sizeof(authPin));
    prefs.end();
    logLine(LOG_AUTH, LOG_INFO, "PIN взят из флеш, защита включена");
    return;
  }

  // Во флеш пусто: это первый запуск после заливки. Берём из secrets.h и запоминаем.
  if (authValidPin(STAFF_PIN)) {
    strlcpy(authPin, STAFF_PIN, sizeof(authPin));
    prefs.putString("pin", authPin);
    logLine(LOG_AUTH, LOG_INFO, "PIN взят из secrets.h и записан во флеш, защита включена");
  } else if (strlen(STAFF_PIN) == 0) {
    authPin[0] = 0;
    logLine(LOG_AUTH, LOG_WARN, "PIN не задан, посох отвечает всем подряд");
  } else {
    authPin[0] = 0;
    logLine(LOG_AUTH, LOG_ERROR, "PIN в secrets.h не годится, нужно ровно %d цифр. Посох отвечает всем подряд",
            PIN_LENGTH);
  }
  prefs.end();
}

// Сколько секунд ещё нельзя пробовать PIN. Ноль значит «пробуй».
// Сравнение вычитанием, а не «меньше»: millis() переполняется через 49 суток,
// и прямое сравнение в этот момент дало бы вечную блокировку.
uint32_t authWaitSeconds() {
  if (!authBlocked) return 0;
  uint32_t now = millis();
  if ((int32_t)(now - authBlockedUntil) >= 0) {
    authBlocked = false;
    return 0;
  }
  return (authBlockedUntil - now + 999) / 1000;   // вверх, чтобы не обещать «0 секунд»
}

// Проверяет PIN. Верный обнуляет счётчик промахов, неверный двигает лестницу пауз.
// Саму паузу вызывающий не назначает: всё считается здесь, иначе правила расползутся.
bool authTry(const char* pin) {
  if (!pin) pin = "";
  if (strcmp(pin, authPin) == 0) {
    authMisses  = 0;
    authBlocked = false;
    return true;
  }

  authMisses++;
  uint32_t pause = 0;
  if (authMisses % 3 == 0) {
    if      (authMisses == 3) pause = AUTH_BLOCK_3_S;
    else if (authMisses == 6) pause = AUTH_BLOCK_6_S;
    else if (authMisses == 9) pause = AUTH_BLOCK_9_S;
    else                      pause = AUTH_BLOCK_12_S;
  }
  if (pause) {
    authBlockedUntil = millis() + pause * 1000UL;
    authBlocked      = true;
    logLine(LOG_AUTH, LOG_WARN, "неверный PIN, промах %d подряд, пауза %u с", authMisses, pause);
  } else {
    logLine(LOG_AUTH, LOG_WARN, "неверный PIN, промах %d подряд", authMisses);
  }
  return false;
}

// Меняет PIN. Возвращает nullptr при успехе, иначе причину отказа словами.
// Старый PIN обязателен даже для того, кто уже вошёл: команда должна быть безопасна,
// если планшет оставили без присмотра открытым.
const char* authChange(const char* oldPin, const char* newPin) {
  if (strcmp(oldPin ? oldPin : "", authPin) != 0) return "старый PIN не совпадает";
  if (!authValidPin(newPin))                      return "новый PIN должен быть из четырёх цифр";

  strlcpy(authPin, newPin, sizeof(authPin));
  Preferences prefs;
  prefs.begin("staff", false);
  prefs.putString("pin", authPin);
  prefs.end();
  authMisses  = 0;
  authBlocked = false;
  logLine(LOG_AUTH, LOG_INFO, "PIN изменён с планшета");
  return nullptr;
}
