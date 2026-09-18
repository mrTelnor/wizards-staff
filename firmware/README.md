# Прошивка: среда разработки

Скетч лежит в папке `wizards_staff/`. Имя папки совпадает с именем файла `wizards_staff.ino`, так требует Arduino IDE. Все номера ножек, адреса и тайминги собраны в `config.h`, в остальном коде номеров нет.

## Что установить

1. **Arduino IDE 2.x** с [arduino.cc](https://www.arduino.cc/en/software). На Windows можно через winget:

   ```powershell
   winget install --id ArduinoSA.IDE.stable --exact --accept-package-agreements --accept-source-agreements
   ```

2. **Пакет плат ESP32.** File → Preferences → Additional boards manager URLs, вставить:

   ```
   https://espressif.github.io/arduino-esp32/package_esp32_index.json
   ```

   Затем Boards Manager (вторая иконка слева), поиск `esp32`, установить «esp32 by Espressif Systems» версии 3.x.

3. **Библиотеки.** Library Manager (третья иконка слева), по очереди установить, на вопрос о зависимостях отвечать «Install all»:
   - Adafruit GFX Library
   - Adafruit ST7735 and ST7789 Library
   - Adafruit NeoPixel
   - Adafruit PCF8574
   - RTClib
   - NimBLE-Arduino (h2zero), связь с телефоном по Bluetooth, проверено на 2.5.1
   - ArduinoJson (Benoit Blanchon), обмен сообщениями с телефоном, проверено на 7.4.3

## Настройки платы

Плата ESP32-C3 SuperMini подключается кабелем USB-C с передачей данных, а не «только зарядка».

- Tools → Board → esp32 → **ESP32C3 Dev Module**
- Tools → Port → появившийся COM-порт
- Tools → **USB CDC On Boot → Enabled**, иначе Serial-монитор молчит. Этот пункт слетает в Disabled при перевыборе платы или порта, проверять, если монитор пуст
- Tools → **Partition Scheme → Minimal SPIFFS (1.9MB APP with OTA)/190KB SPIFFS**: прошивка с Bluetooth не помещается в стандартный раздел

Если порт не появился: зажать на плате кнопку BOOT, не отпуская подключить USB, отпустить. Плата войдёт в режим загрузчика. После заливки нажать RESET.

## Заливка

File → Open → `firmware/wizards_staff/wizards_staff.ino`, затем кнопка Upload. В конце лога «Hard resetting via RTS pin» или «Done uploading». Serial Monitor на скорости 115200.

При заливке от USB тумблер питания посоха должен быть выключен: у SuperMini нет диода между USB и ножкой 5V, кормить плату с двух сторон одновременно нельзя.

## Файлы скетча

| Файл | Что делает |
|---|---|
| `wizards_staff.ino` | главный цикл и машина состояний |
| `config.h` | ножки, адреса I2C, тайминги, таблица костей |
| `dice.h` | бросок костей, история, самопроверка генератора |
| `buttons.h` | 11 кнопок через PCF8574, дребезг, короткое и долгое нажатие |
| `display.h` | экраны: часы, взведён, анимация, результат, история |
| `strike.h` | датчик удара SW-420 |
| `lights.h` | кольцо WS2812B: режимы, цвета, анимация |
| `sound.h` | пищалка: тик, аккорд, фанфара |
| `rtc.h` | часы DS3231 |
| `battery.h` | замер заряда через делитель |
| `settings.h` | меню настроек и сохранение во флеш |

Файлы появляются по мере сборки.

## Инструменты

Папка `tools/` содержит отдельные скетчи-помощники, каждый в своей папке. Они не входят в прошивку посоха.

| Скетч | Что делает |
|---|---|
| `tools/battery_log/` | пишет напряжение аккумулятора раз в 30 с в файл во флеш платы (LittleFS), читается командой `dump` в Serial Monitor. Для замера автономности без компьютера |
