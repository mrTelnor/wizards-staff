#pragma once
#include <NimBLEDevice.h>
#include <ArduinoJson.h>
#include "config.h"
#include "log.h"

// Связь с телефоном по Bluetooth Low Energy (протокол: docs/ПЛАН-APP.md, раздел «Протокол»).
// Сервис Nordic UART: характеристика RX принимает JSON-команды от телефона,
// характеристика TX отдаёт JSON-события. Одно сообщение это одна строка, в конце '\n'.
// Стандартный Battery Service дублирует процент заряда, чтобы его показывало меню Bluetooth телефона.

static const char* BLE_DEVICE_NAME  = "WizardsStaff";
static const char* NUS_SERVICE_UUID = "6E400001-B5A3-F393-E0A9-E50E24DCCA9E";
static const char* NUS_RX_UUID      = "6E400002-B5A3-F393-E0A9-E50E24DCCA9E";   // телефон → посох
static const char* NUS_TX_UUID      = "6E400003-B5A3-F393-E0A9-E50E24DCCA9E";   // посох → телефон

NimBLEServer*         bleServer  = nullptr;
NimBLECharacteristic* bleTx      = nullptr;
NimBLECharacteristic* bleBattery = nullptr;
bool     bleConnected    = false;
uint16_t bleConnHandle   = 0;       // номер соединения, нужен, чтобы узнать размер пакета
String   bleRxBuffer;               // накопленный текст команды, пока она не дописана до конца
int      bleBraceDepth   = 0;       // сколько фигурных скобок JSON сейчас открыто

// Очередь принятых команд. Телефон при подключении шлёт несколько команд подряд, и вторая
// приходит раньше, чем главный цикл успевает разобрать первую. С одной ячейкой такие команды
// терялись: на этом пропадала синхронизация часов.
const int BLE_QUEUE_SIZE = 6;
String    bleQueue[BLE_QUEUE_SIZE];
int       bleQueueHead = 0;         // откуда читаем
int       bleQueueTail = 0;         // куда пишем

// Определяется в главном скетче: что делать с пришедшей командой.
void bleOnCommand(JsonDocument& cmd);

// Телефон подключился или отключился. После отключения снова становимся видимыми.
class StaffServerCallbacks : public NimBLEServerCallbacks {
  void onConnect(NimBLEServer* server, NimBLEConnInfo& info) override {
    bleConnected  = true;
    bleConnHandle = info.getConnHandle();
    logLine(LOG_BLE, LOG_INFO, "планшет подключился");
  }
  void onDisconnect(NimBLEServer* server, NimBLEConnInfo& info, int reason) override {
    bleConnected = false;
    bleQueueHead = bleQueueTail;   // недоразобранные команды прошлого сеанса не нужны
    bleRxBuffer = "";
    bleBraceDepth = 0;
    logLine(LOG_BLE, LOG_INFO, "планшет отключился, причина %d", reason);
    NimBLEDevice::startAdvertising();
  }
};

// Складывает накопленную команду в очередь и освобождает буфер под следующую.
void bleQueuePush() {
  if (!bleRxBuffer.length()) return;
  int next = (bleQueueTail + 1) % BLE_QUEUE_SIZE;
  if (next == bleQueueHead) {
    logLine(LOG_BLE, LOG_WARN, "очередь команд переполнена, команда потеряна");
  } else {
    bleQueue[bleQueueTail] = bleRxBuffer;
    bleQueueTail = next;
  }
  bleRxBuffer = "";
  bleBraceDepth = 0;
}

// Телефон записал данные в RX: складываем в буфер. Команда закончена, когда пришёл перевод строки
// или когда закрылась последняя фигурная скобка JSON: терминалы вроде BLE Scanner не умеют слать '\n'.
class StaffRxCallbacks : public NimBLECharacteristicCallbacks {
  void onWrite(NimBLECharacteristic* c, NimBLEConnInfo& info) override {
    std::string v = c->getValue();
    for (char ch : v) {
      if (ch == '\n' || ch == '\r') {
        bleQueuePush();
        continue;
      }
      if (bleRxBuffer.length() < 512) bleRxBuffer += ch;
      if (ch == '{') bleBraceDepth++;
      if (ch == '}' && --bleBraceDepth <= 0) bleQueuePush();
    }
  }
};

// Запускает BLE: имя, сервисы, реклама. Вызывать один раз в setup().
void bleBegin() {
  NimBLEDevice::init(BLE_DEVICE_NAME);
  NimBLEDevice::setPower(3);   // 3 дБм: посох рядом с телефоном, больше не нужно

  bleServer = NimBLEDevice::createServer();
  bleServer->setCallbacks(new StaffServerCallbacks());

  NimBLEService* nus = bleServer->createService(NUS_SERVICE_UUID);
  bleTx = nus->createCharacteristic(NUS_TX_UUID, NIMBLE_PROPERTY::NOTIFY);
  NimBLECharacteristic* rx = nus->createCharacteristic(NUS_RX_UUID,
      NIMBLE_PROPERTY::WRITE | NIMBLE_PROPERTY::WRITE_NR);
  rx->setCallbacks(new StaffRxCallbacks());

  NimBLEService* bat = bleServer->createService(NimBLEUUID((uint16_t)0x180F));
  bleBattery = bat->createCharacteristic(NimBLEUUID((uint16_t)0x2A19),
      NIMBLE_PROPERTY::READ | NIMBLE_PROPERTY::NOTIFY);

  bleServer->start();   // в NimBLE 2.x сервисы стартуют вместе с сервером

  // Рекламный пакет BLE это 31 байт. В него кладём флаги и имя: имя видит любой сканер.
  // Длинный UUID сервиса UART уходит в ответ на сканирование, это второй пакет; Android
  // склеивает оба, поэтому поиск посоха по UUID в приложении работает.
  NimBLEAdvertising* adv = NimBLEDevice::getAdvertising();
  NimBLEAdvertisementData advData;
  advData.setFlags(BLE_HS_ADV_F_DISC_GEN | BLE_HS_ADV_F_BREDR_UNSUP);
  advData.setName(BLE_DEVICE_NAME);
  adv->setAdvertisementData(advData);
  NimBLEAdvertisementData scanData;
  scanData.addServiceUUID(NimBLEUUID(NUS_SERVICE_UUID));
  adv->setScanResponseData(scanData);
  bool ok = adv->start();
  logLine(LOG_BLE, ok ? LOG_INFO : LOG_ERROR, "реклама %s, имя %s, адрес %s",
          ok ? "запущена" : "не запустилась", BLE_DEVICE_NAME,
          NimBLEDevice::getAddress().toString().c_str());
}

// Свой адрес BLE строкой вида "3c:0f:02:a3:f9:8a". Приложение видит этот же адрес
// при сканировании и запоминает по нему сохранённый PIN, поэтому берём именно адрес BLE,
// а не базовый MAC чипа: на ESP32 они отличаются.
// Вызывать только после bleBegin().
String bleMac() {
  return String(NimBLEDevice::getAddress().toString().c_str());
}

// Отправляет JSON телефону одной строкой. Если телефон не подключён, молча пропускает.
// BLE-пакет маленький, поэтому строка режется на куски по размеру согласованного пакета.
void bleSend(const JsonDocument& doc) {
  if (!bleConnected || !bleTx) return;
  String line;
  serializeJson(doc, line);
  line += '\n';
  size_t chunk = bleServer->getPeerMTU(bleConnHandle);
  chunk = chunk > 23 ? chunk - 3 : 20;
  for (size_t i = 0; i < line.length(); i += chunk) {
    size_t len = min(chunk, line.length() - i);
    bleTx->setValue((const uint8_t*)line.c_str() + i, len);
    bleTx->notify();
    delay(5);
  }
}

// Заряд: и в JSON, и в стандартную характеристику Battery Level.
void bleSendBattery(int mv, int pct) {
  if (bleBattery) {
    uint8_t level = (uint8_t)pct;
    bleBattery->setValue(&level, 1);
    if (bleConnected) bleBattery->notify();
  }
  JsonDocument doc;
  doc["ev"]  = "bat";
  doc["mv"]  = mv;
  doc["pct"] = pct;
  bleSend(doc);
}

// Строка для журнала телефона: то же, что посох печатает в монитор порта.
// Так ложные срабатывания датчика и удары мимо взвода видно на планшете, без провода.
void bleSendLog(const char* msg) {
  JsonDocument doc;
  doc["ev"]  = "log";
  doc["msg"] = msg;
  bleSend(doc);
}

// Короткий ответ об ошибке: телефон увидит, что команда не понята.
void bleSendError(const char* msg) {
  JsonDocument doc;
  doc["ev"]  = "err";
  doc["msg"] = msg;
  bleSend(doc);
}

// Вызывать в каждом обороте loop(): берёт из очереди одну команду, разбирает и отдаёт
// в bleOnCommand. По одной за оборот: обороты идут каждые пару миллисекунд, очередь не копится.
void bleLoop() {
  if (bleQueueHead == bleQueueTail) return;
  String line = bleQueue[bleQueueHead];
  bleQueueHead = (bleQueueHead + 1) % BLE_QUEUE_SIZE;

  JsonDocument cmd;
  DeserializationError err = deserializeJson(cmd, line);
  if (err) {
    logLine(LOG_BLE, LOG_WARN, "не разобрал команду: %s", err.c_str());
    bleSendError("bad json");
    return;
  }
  bleOnCommand(cmd);
}
