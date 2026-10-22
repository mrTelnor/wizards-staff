// Проверка экрана ST7789: перебирает по кругу способы связи с экраном и на каждом
// заливает его красным, зелёным и синим. В монитор порта (115200) пишет номер попытки
// и что пробует. Смотришь на экран: на какой попытке замелькали цвета, та и рабочая.
//
// Зачем отдельный скетч: у модуля без ножки CS режим SPI зависит от производителя,
// и подбирать его перезаливкой основной прошивки по одному варианту долго.
//
// Попытки:
//   1–4  аппаратный SPI, режимы 0, 1, 2, 3, медленно (1 МГц);
//   5    программный SPI: плата сама дёргает ножки SCL и SDA, без блока SPI.
// Если цвета есть только на пятой — провода в порядке, дело в настройке аппаратного SPI.
// Если нигде нет — смотреть провода SCL, SDA, DC, RST и пайку гребёнки.
//
// Ножки те же, что в firmware/wizards_staff/config.h. Скетч самостоятельный,
// как tools/battery_log, поэтому номера продублированы здесь.
#include <SPI.h>
#include <Adafruit_GFX.h>
#include <Adafruit_ST7789.h>

const int PIN_TFT_SCK  = 4;    // SCL экрана
const int PIN_TFT_BLK  = 5;    // BL экрана, подсветка
const int PIN_TFT_MOSI = 6;    // SDA экрана
const int PIN_TFT_DC   = 7;    // DC экрана
const int PIN_TFT_RST  = 10;   // RST экрана

const uint32_t SLOW_HZ  = 1000000;   // медленно и надёжно: провода на макетке не помеха
const int      COLOR_MS = 700;       // сколько держать каждый цвет

// CS у модуля на земле, поэтому первым аргументом -1.
Adafruit_ST7789 hardTft(-1, PIN_TFT_DC, PIN_TFT_RST);
Adafruit_ST7789 softTft(-1, PIN_TFT_DC, PIN_TFT_MOSI, PIN_TFT_SCK, PIN_TFT_RST);

// Заливает экран тремя цветами по очереди, потом чёрным.
void showColors(Adafruit_ST7789& tft) {
  const uint16_t colors[] = { ST77XX_RED, ST77XX_GREEN, ST77XX_BLUE };
  for (uint16_t c : colors) {
    tft.fillScreen(c);
    delay(COLOR_MS);
  }
  tft.fillScreen(ST77XX_BLACK);
}

// Одна попытка через аппаратный SPI в заданном режиме.
void tryHardware(int attempt, uint8_t mode, int modeNumber) {
  Serial.printf("Попытка %d: аппаратный SPI, режим %d, 1 МГц\n", attempt, modeNumber);
  SPI.begin(PIN_TFT_SCK, -1, PIN_TFT_MOSI, -1);
  hardTft.init(240, 240, mode);
  hardTft.setSPISpeed(SLOW_HZ);
  showColors(hardTft);
  SPI.end();   // отпускаем ножки, чтобы следующая попытка начала с чистого листа
}

// Попытка через программный SPI: ножки дёргает сама плата.
void trySoftware(int attempt) {
  Serial.printf("Попытка %d: программный SPI\n", attempt);
  softTft.init(240, 240);
  showColors(softTft);
}

void setup() {
  Serial.begin(115200);
  delay(1500);
  pinMode(PIN_TFT_BLK, OUTPUT);
  digitalWrite(PIN_TFT_BLK, HIGH);   // подсветка на полную все попытки подряд
  Serial.println("Проверка экрана ST7789. Смотри на экран: на какой попытке видны цвета?");
}

void loop() {
  tryHardware(1, SPI_MODE0, 0);
  delay(1500);
  tryHardware(2, SPI_MODE1, 1);
  delay(1500);
  tryHardware(3, SPI_MODE2, 2);
  delay(1500);
  tryHardware(4, SPI_MODE3, 3);
  delay(1500);
  trySoftware(5);
  delay(1500);
  Serial.println("Круг закончен, начинаю заново");
}
