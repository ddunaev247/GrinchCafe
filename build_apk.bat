@echo off
REM Build GrinchCafe, GrinchCafeWaiter and GrinchCafeAdmin APKs
REM Output copies to project root for easy access
setlocal enabledelayedexpansion

if exist "%~dp0jdk8\jdk1.8.0_502" (
  set "JAVA_HOME=%~dp0jdk8\jdk1.8.0_502"
)

if defined JAVA_HOME (
  echo Using JAVA_HOME=%JAVA_HOME%
  set "PATH=%JAVA_HOME%\bin;%PATH%"
) else (
  echo WARNING: JAVA_HOME not set. Using system Java if available.
)

call "%~dp0gradlew.bat" assembleDebug --no-daemon
if errorlevel 1 (
  echo Build failed.
  exit /b 1
)

set "CAFE_BUILD=%~dp0app\build\outputs\apk\debug\GrinchCafe-debug.apk"
set "WAITER_BUILD=%~dp0waiter\build\outputs\apk\debug\GrinchCafeWaiter-debug.apk"
set "ADMIN_BUILD=%~dp0admin\build\outputs\apk\debug\GrinchCafeAdmin-debug.apk"
REM set "KARAOKE_BUILD=%~dp0karaoke\build\outputs\apk\debug\GrinchKaraoke-debug.apk"
set "CAFE_OUT=%~dp0GrinchCafe-debug.apk"
set "WAITER_OUT=%~dp0GrinchCafeWaiter-debug.apk"
set "ADMIN_OUT=%~dp0GrinchCafeAdmin-debug.apk"
REM set "KARAOKE_OUT=%~dp0GrinchKaraoke-debug.apk"

if not exist "%CAFE_BUILD%" (
  set "CAFE_BUILD=%~dp0app\build\outputs\apk\debug\app-debug.apk"
)

if exist "%CAFE_BUILD%" (
  copy /Y "%CAFE_BUILD%" "%CAFE_OUT%" >nul
  echo GrinchCafe:       %CAFE_OUT%
) else (
  echo GrinchCafe APK not found
  exit /b 1
)

if exist "%WAITER_BUILD%" (
  copy /Y "%WAITER_BUILD%" "%WAITER_OUT%" >nul
  echo GrinchCafeWaiter: %WAITER_OUT%
) else (
  echo GrinchCafeWaiter APK not found - check waiter module build
  exit /b 1
)

if exist "%ADMIN_BUILD%" (
  copy /Y "%ADMIN_BUILD%" "%ADMIN_OUT%" >nul
  echo GrinchCafeAdmin:   %ADMIN_OUT%
) else (
  echo GrinchCafeAdmin APK not found - check admin module build
  exit /b 1
)

REM if not exist "%KARAOKE_BUILD%" (
REM  set "KARAOKE_BUILD=%~dp0karaoke\build\outputs\apk\debug\karaoke-debug.apk"
REM )

REM if exist "%KARAOKE_BUILD%" (
REM  copy /Y "%KARAOKE_BUILD%" "%KARAOKE_OUT%" >nul
REM  echo GrinchKaraoke:   %KARAOKE_OUT%
REM) else (
REM  echo GrinchKaraoke APK not found - check karaoke module build
REM  exit /b 1
REM )

if not "%~1"=="" copy /Y "%CAFE_OUT%" "%~1" >nul
if not "%~2"=="" copy /Y "%WAITER_OUT%" "%~2" >nul
if not "%~3"=="" copy /Y "%ADMIN_OUT%" "%~3" >nul
REM if not "%~4"=="" copy /Y "%KARAOKE_OUT%" "%~4" >nul

endlocal
exit /b 0
