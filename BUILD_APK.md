# Инструкции по сборке APK

Коротко: используйте скрипт `build_apk.bat` на Windows или `build_apk.sh` на Unix-подобных системах для автоматической сборки debug APK и (опционально) копирования его в указанное место.

Файлы добавлены в корень репозитория:

- `build_apk.bat` — Windows (Powershell / CMD)
- `build_apk.sh` — Unix / Git Bash / WSL

Предпосылки:
- Java 8 доступна либо в переменной окружения `JAVA_HOME`, либо в папке `jdk8/jdk1.8.0_502` рядом со скриптами.
- Android SDK путь указан в `local.properties` (обычно `sdk.dir=C:/Users/<user>/AppData/Local/Android/Sdk`).
- В репозитории присутствует `gradlew`/`gradlew.bat`.

Запуск (Windows CMD / Powershell):

```bat
cd %HOMEPATH%\path\to\DeviceInfoApp\DeviceInfoApp
build_apk.bat
```

Скопировать APK в указанную папку:

```bat
build_apk.bat C:\path\to\usb\drive\app-debug.apk
```

Запуск (Unix / Git Bash / WSL):

```bash
cd /path/to/DeviceInfoApp/DeviceInfoApp
./build_apk.sh
```

Скопировать APK в указанную папку:

```bash
./build_apk.sh /mnt/e/usb/app-debug.apk
```

Пояснения для интеграции с ИИ / автосборкой:
- Скрипт возвращает ненулевой код при ошибке сборки — агент/система CI может по нему определить неудачу.
- Можно передавать путь назначения (параметр 1) — удобно для автоматического копирования на флешку или в облако.
- Для крупных проектов возможны ошибки D8 OutOfMemory; в репозитории добавлен `gradle.properties`, увеличивающий `org.gradle.jvmargs=-Xmx3g`. При нехватке памяти увеличьте это значение.

Если нужно, могу добавить ещё: автоматическое подпись release-ключом, сборку release, или интеграцию с CI (GitHub Actions). Просто скажите, что предпочитаете.
