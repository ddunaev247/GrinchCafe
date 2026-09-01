@echo off
setlocal
set "JAVA_HOME=C:\Users\danii\Downloads\DeviceInfoApp\DeviceInfoApp\jdk8\jdk1.8.0_502"
set "PATH=%JAVA_HOME%\bin;%PATH%"
call gradlew.bat assembleDebug --no-daemon --stacktrace --info
endlocal
