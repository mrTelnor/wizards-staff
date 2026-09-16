// Логгер заряда аккумулятора. Инструмент для замера автономности, в посох не идёт.
//
// Раз в LOG_PERIOD_MS меряет напряжение аккумулятора через делитель на PIN_BATTERY
// и дописывает строку в файл /battery.csv во флеш-памяти платы (LittleFS).
// Работает без компьютера: включили тумблер, оставили на несколько часов.
// Потом тумблер выключить, подключить USB, открыть Serial Monitor (115200, окончание строки New Line)
// и набрать команду:
//   dump   напечатать весь лог
//   stat   размер файла и число строк
//   now    измерить и напечатать прямо сейчас
//   clear  стереть лог
//
// Формат строки: номер_включения;секунд_с_включения;милливольты;проценты
//
// В Arduino IDE: плата ESP32C3 Dev Module, USB CDC On Boot: Enabled,
// Partition Scheme: Default 4MB with spiffs (стоит по умолчанию), под файлы отдано 1,5 МБ.
// Ножки и границы заряда те же, что в firmware/wizards_staff/config.h.

#include <LittleFS.h>
#include <Preferences.h>

const int PIN_BATTERY = 3;   // середина делителя 100 кОм + 100 кОм
const int PIN_LED     = 8;   // встроенный светодиод, горит при LOW

const unsigned long LOG_PERIOD_MS = 30000;    // как часто писать
const int  BATTERY_FULL_MV  = 4200;           // 100 %
const int  BATTERY_EMPTY_MV = 3300;           // 0 %
const size_t LOG_MAX_BYTES  = 1000000;        // дальше не пишем, чтобы не забить флеш

const char* LOG_PATH = "/battery.csv";

Preferences prefs;
uint32_t bootCount = 0;          // сколько раз плату включали, чтобы отличать сессии в логе
unsigned long lastLogAt = 0;
bool logFull = false;
bool fsOk = false;               // файловая система поднялась

// Настраивает вход: 12 бит и диапазон до ~2,5 В.
void batteryBegin() {
  analogReadResolution(12);
  analogSetPinAttenuation(PIN_BATTERY, ADC_11db);
}

// Напряжение аккумулятора в милливольтах: делитель делит пополам, усредняем 8 замеров.
int batteryMillivolts() {
  long sum = 0;
  for (int i = 0; i < 8; i++) sum += analogReadMilliVolts(PIN_BATTERY);
  return (sum / 8) * 2;
}

// Процент заряда по линейной шкале между BATTERY_EMPTY_MV и BATTERY_FULL_MV.
int batteryPercent(int mv) {
  return constrain(map(mv, BATTERY_EMPTY_MV, BATTERY_FULL_MV, 0, 100), 0, 100);
}

// Одна запись в лог. Возвращает false, если записать не удалось.
bool logAppend() {
  if (!fsOk || logFull) return false;
  File f = LittleFS.open(LOG_PATH, FILE_APPEND);
  if (!f) return false;
  if (f.size() > LOG_MAX_BYTES) {
    f.close();
    logFull = true;
    Serial.println("Лог заполнен, запись остановлена. Набери clear, чтобы стереть.");
    return false;
  }
  int mv = batteryMillivolts();
  f.printf("%lu;%lu;%d;%d\n", (unsigned long)bootCount, millis() / 1000, mv, batteryPercent(mv));
  f.close();
  return true;
}

// Печатает файл целиком в Serial.
void logDump() {
  File f = LittleFS.open(LOG_PATH, FILE_READ);
  if (!f) { Serial.println("Лога нет."); return; }
  Serial.println("boot;sec;mV;pct");
  while (f.available()) Serial.write(f.read());
  f.close();
  Serial.println("--- конец лога ---");
}

// Размер файла и число строк.
void logStat() {
  File f = LittleFS.open(LOG_PATH, FILE_READ);
  if (!f) { Serial.println("Лога нет."); return; }
  size_t lines = 0;
  while (f.available()) if (f.read() == '\n') lines++;
  Serial.printf("Файл %s: %u байт, %u строк. Свободно во флеш: %u байт.\n",
                LOG_PATH, (unsigned)f.size(), (unsigned)lines,
                (unsigned)(LittleFS.totalBytes() - LittleFS.usedBytes()));
  f.close();
}

// Стирает лог и сбрасывает счётчик включений.
void logClear() {
  LittleFS.remove(LOG_PATH);
  prefs.begin("batlog", false);
  prefs.putUInt("boot", 0);
  prefs.end();
  bootCount = 0;
  logFull = false;
  Serial.println("Лог стёрт.");
}

// Разбирает команду из Serial.
void handleCommand(String cmd) {
  cmd.trim();
  if (cmd == "dump")  logDump();
  else if (cmd == "stat") logStat();
  else if (cmd == "clear") logClear();
  else if (cmd == "now") {
    int mv = batteryMillivolts();
    Serial.printf("Сейчас: %d мВ, %d %%\n", mv, batteryPercent(mv));
  } else if (cmd.length()) {
    Serial.println("Команды: dump, stat, now, clear");
  }
}

void setup() {
  Serial.begin(115200);
  pinMode(PIN_LED, OUTPUT);
  digitalWrite(PIN_LED, HIGH);
  delay(2000);   // даём Serial Monitor подключиться, если он есть
  Serial.println("Логгер заряда: старт");
  batteryBegin();

  // Файловая система. Сначала пробуем смонтировать как есть; если раздел ещё не размечен,
  // форматируем отдельным шагом и пишем об этом, чтобы было видно, где мы находимся.
  fsOk = LittleFS.begin(false);
  if (!fsOk) {
    Serial.println("Раздел не размечен, форматирую (несколько секунд)...");
    if (LittleFS.format()) fsOk = LittleFS.begin(false);
  }
  if (fsOk) {
    Serial.printf("LittleFS: %u КБ всего, %u КБ занято\n",
                  (unsigned)(LittleFS.totalBytes() / 1024), (unsigned)(LittleFS.usedBytes() / 1024));
  } else {
    Serial.println("ОШИБКА: LittleFS не поднялась. Проверь Partition Scheme в Tools.");
  }

  // Счётчик включений во флеш, чтобы в логе было видно, где началась новая сессия.
  prefs.begin("batlog", false);
  bootCount = prefs.getUInt("boot", 0) + 1;
  prefs.putUInt("boot", bootCount);
  prefs.end();

  Serial.printf("Включение №%lu, запись раз в %lu с в %s\n",
                (unsigned long)bootCount, LOG_PERIOD_MS / 1000, LOG_PATH);
  Serial.println("Команды: dump, stat, now, clear");
  logAppend();   // первая запись сразу при включении
  lastLogAt = millis();
}

void loop() {
  // Периодическая запись. Светодиод коротко вспыхивает при каждой записи.
  if (millis() - lastLogAt >= LOG_PERIOD_MS) {
    lastLogAt = millis();
    if (logAppend()) {
      digitalWrite(PIN_LED, LOW);
      delay(50);
      digitalWrite(PIN_LED, HIGH);
    }
  }

  // Команды из Serial Monitor.
  if (Serial.available()) handleCommand(Serial.readStringUntil('\n'));
  delay(10);
}
