@echo off
rem LiquiWatch trace recorder (round 38).
rem Records a 32-second system trace of the watch (Perfetto) while the Expedition face is up, a song
rem plays and you turn the compass, and puts it with a few notes on the watch's state into
rem expedition_r38.zip in this folder, to send back.
rem Keep this file, trace_config.pbtx and trace_config_basic.pbtx together (unzip the whole
rem tools\trace folder), connect the watch to adb as you do to install from Android Studio, and
rem double-click this file.
setlocal EnableDelayedExpansion
cd /d "%~dp0"
set "NAME=expedition_r38"
set "CFG=trace_config.pbtx"
set "BASIC=trace_config_basic.pbtx"
set "NOW=trace_config_now.pbtx"
set "OUT=/data/misc/perfetto-traces/%NAME%.pftrace"
set "KEEPON="
set "OLDTO="
set "FAILED="
if not exist "%CFG%" goto missing
if not exist "%BASIC%" goto missing

rem ---- Find adb: Android Studio's own first, so the connection to the watch it made is used ----
rem (A different adb, say an older one on PATH, restarts adb and drops the watch's Wi-Fi link.)
set "ADB="
if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
if not defined ADB if defined ANDROID_HOME if exist "%ANDROID_HOME%\platform-tools\adb.exe" set "ADB=%ANDROID_HOME%\platform-tools\adb.exe"
if not defined ADB if defined ANDROID_SDK_ROOT if exist "%ANDROID_SDK_ROOT%\platform-tools\adb.exe" set "ADB=%ANDROID_SDK_ROOT%\platform-tools\adb.exe"
if not defined ADB for /f "delims=" %%p in ('where adb 2^>nul') do if not defined ADB set "ADB=%%p"
if not defined ADB (
  echo Couldn't find adb.exe. Android Studio shows the SDK folder in Settings ^> Languages ^& Frameworks ^> Android SDK;
  echo adb.exe is in its platform-tools folder. Paste its full path here:
  set /p "ADB=adb.exe path: "
)
if defined ADB set "ADB=!ADB:"=!"
if not exist "!ADB!" (
  echo adb.exe isn't at "!ADB!".
  goto end
)
echo Using adb: !ADB!

rem ---- Pick the watch among the connected devices ----------------------------------------
set "TRIES=0"
:scan
echo.
"!ADB!" devices -l
set "SERIAL="
set "EMU="
set "LAST="
set "COUNT=0"
rem (adb lists each device as its name, a tab, and its state.)
for /f "skip=1 tokens=1,2 delims=	" %%a in ('call "!ADB!" devices') do (
  if "%%b"=="device" (
    set /a COUNT+=1
    set "LAST=%%a"
    set "S=%%a"
    for /f "delims=" %%c in ('call "!ADB!" -s "%%a" shell getprop ro.build.characteristics 2^>nul') do (
      set "CH=%%c"
      if not "!CH:watch=!"=="!CH!" (
        if /i "!S:~0,9!"=="emulator-" (
          if not defined EMU set "EMU=%%a"
        ) else (
          if not defined SERIAL set "SERIAL=%%a"
        )
      )
    )
  )
)
if not defined SERIAL if defined EMU set "SERIAL=!EMU!"
if not defined SERIAL if "!COUNT!"=="1" set "SERIAL=!LAST!"
rem (adb may have just started: a watch on Wi-Fi takes a few seconds to come back.)
if "!COUNT!"=="0" if !TRIES! LSS 2 (
  set /a TRIES+=1
  echo Waiting a few seconds for the watch to show up...
  ping -n 6 127.0.0.1 >nul
  goto scan
)
if not defined SERIAL (
  echo.
  if "!COUNT!"=="0" (
    echo No device is listed as "device" above. If the watch shows as "unauthorized", allow
    echo debugging on the watch. If it shows as "offline" or isn't listed, turn Wireless debugging
    echo off and on again on the watch, connect it from Android Studio, then run this again.
    goto end
  )
  echo Couldn't tell which device is the watch. Copy its name from the first column above:
  set /p "SERIAL=device: "
)
if defined SERIAL set "SERIAL=!SERIAL:"=!"
if not defined SERIAL (
  echo No device was chosen.
  goto end
)
set A="!ADB!" -s "!SERIAL!"
set "MODEL="
for /f "delims=" %%m in ('call !A! shell getprop ro.product.model 2^>nul') do set "MODEL=%%m"
echo Using device: !SERIAL! !MODEL!

rem ---- The companion of round 38, the face of round 37 and Cover Display's watch app of round 36? ----
set "OLDV="
rem (Package and version joined by a colon: an equals sign would split the list.)
for %%p in (com.liquiwatch.companion:1.0-r38 com.liquiwatch.face.expedition:1.0-r37 com.coverflow.app:1.0-r36) do (
  for /f "tokens=1,2 delims=:" %%a in ("%%p") do (
    set "V="
    for /f "tokens=2 delims==" %%v in ('call !A! shell "dumpsys package %%a | grep -m 1 versionName"') do set "V=%%v"
    if not "!V!"=="%%b" set "OLDV=!OLDV! %%a=!V! (wants %%b)"
  )
)
if defined OLDV (
  echo.
  echo Not the versions this recorder is for:!OLDV!
  echo Install the LiquiWatch companion of round 38, the Expedition face of round 37 and Cover
  echo Display's watch app of round 36 from Android Studio first, then run this again. Or press a
  echo key to record anyway.
  pause
)

rem ---- What's on the watch, for the notes -------------------------------------------------
> info.txt echo LiquiWatch trace recorder, round 38
>> info.txt echo PC time: %DATE% %TIME%
>> info.txt echo device: !SERIAL! !MODEL!
!A! shell getprop ro.build.fingerprint >> info.txt 2>&1
for %%p in (com.samsung.wear.watchface.runtime com.liquiwatch.companion com.liquiwatch.face.expedition com.coverflow.app) do (
  >> info.txt echo %%p:
  !A! shell "dumpsys package %%p | grep -E 'versionName|versionCode|lastUpdateTime'" >> info.txt 2>&1
)
!A! shell "perfetto --version 2>&1 | head -n 2" >> info.txt 2>&1

rem ---- The trace's settings: AIDL's calls only if this watch knows them -------------------
set "AIDL="
for /f "tokens=1" %%c in ('call !A! shell "atrace --list_categories 2>/dev/null"') do if /i "%%c"=="aidl" set "AIDL=1"
if defined AIDL (
  copy /y "%CFG%" "%NOW%" >nul
) else (
  findstr /v /i /c:"aidl" "%CFG%" > "%NOW%"
)
>> info.txt echo aidl category: !AIDL!

rem ---- Keep the screen on while it records (put back after) --------------------------------
for /f "delims=" %%t in ('call !A! shell settings get system screen_off_timeout 2^>nul') do set "OLDTO=%%t"
set "X=0"
set /a "X=OLDTO" 2>nul
if "!X!"=="!OLDTO!" if !X! GTR 0 set "KEEPON=1"
>> info.txt echo screen timeout before: !OLDTO!
if defined KEEPON !A! shell settings put system screen_off_timeout 120000 >nul 2>&1

echo.
echo  1. On the watch: the Expedition face, and a song playing - the thin line round the
echo     compass moving. The watch's screen stays on while this records, and is put back after.
echo  2. Wake the watch, then press a key here. Then, watching the watch:
echo       the first 10 seconds:  hold your wrist still
echo       when it buzzes once:   turn your wrist left and right, as when reading the compass
echo       when it buzzes twice:  hold still again, until it buzzes three times
echo     Don't touch the watch's screen while it records. What the watch logs in those 32
echo     seconds is in the recording too.
echo.
pause

if exist "%NAME%.pftrace" del "%NAME%.pftrace"
if exist "%NAME%.zip" del "%NAME%.zip"
!A! push "%NOW%" /data/local/tmp/r38_config.pbtx >nul
!A! shell rm -f %OUT% >nul 2>&1
!A! shell dumpsys gfxinfo com.samsung.wear.watchface.runtime reset >nul 2>&1
echo Starting the recording...
!A! shell "cat /data/local/tmp/r38_config.pbtx | perfetto --txt -c - -o %OUT% --background-wait" > perfetto_log.txt 2>&1
set "RC=%ERRORLEVEL%"
rem (If it didn't say it started but a recording is running anyway, that one is used.)
if not "%RC%"=="0" (
  set "PF="
  for /f %%p in ('call !A! shell pidof perfetto 2^>nul') do set "PF=%%p"
  if defined PF set "RC=0"
)
if not "!RC!"=="0" (
  echo The full recording didn't start, so the simpler one...
  >> info.txt echo full recording did not start: simpler one
  !A! push "%BASIC%" /data/local/tmp/r38_basic.pbtx >nul
  !A! shell "cat /data/local/tmp/r38_basic.pbtx | perfetto --txt -c - -o %OUT% --background-wait" >> perfetto_log.txt 2>&1
  set "RC=!ERRORLEVEL!"
)
if not "!RC!"=="0" (
  set "PF="
  for /f %%p in ('call !A! shell pidof perfetto 2^>nul') do set "PF=%%p"
  if defined PF set "RC=0"
)
if not "!RC!"=="0" (
  !A! shell "cat /data/local/tmp/r38_basic.pbtx | perfetto --txt -c - -o %OUT% --background" >> perfetto_log.txt 2>&1
  set "RC=!ERRORLEVEL!"
)
if not "!RC!"=="0" (
  echo.
  echo The watch wouldn't start a recording. What it said is in perfetto_log.txt: send that
  echo and info.txt from this folder.
  set "FAILED=1"
  goto restore
)

echo.
echo Recording. Hold your wrist still...
ping -n 11 127.0.0.1 >nul
!A! shell cmd vibrator_manager synced -f oneshot 150 >nul 2>&1
echo Turn your wrist left and right, as when reading the compass...
ping -n 11 127.0.0.1 >nul
!A! shell "cmd vibrator_manager synced -f oneshot 120; sleep 0.3; cmd vibrator_manager synced -f oneshot 120" >nul 2>&1
echo Hold still again...
ping -n 11 127.0.0.1 >nul
echo Finishing...
set "W=0"
:wait
set "PF="
for /f %%p in ('call !A! shell pidof perfetto 2^>nul') do set "PF=%%p"
if defined PF if !W! LSS 90 (
  set /a W+=2
  ping -n 3 127.0.0.1 >nul
  goto wait
)
!A! shell "cmd vibrator_manager synced -f oneshot 100; sleep 0.25; cmd vibrator_manager synced -f oneshot 100; sleep 0.25; cmd vibrator_manager synced -f oneshot 100" >nul 2>&1

!A! pull %OUT% "%NAME%.pftrace"
!A! shell dumpsys gfxinfo com.samsung.wear.watchface.runtime > gfxinfo.txt 2>&1
!A! shell dumpsys meminfo com.samsung.wear.watchface.runtime > meminfo_runtime.txt 2>&1
!A! shell "ps -T -p $(pidof com.samsung.wear.watchface.runtime) -o TID,PRI,NI,RTPRIO,SCH,PCY,TIME,CMD" > threads.txt 2>&1
rem (The watch's log as text too: which app it froze, what Cover Display and the companion did.)
!A! logcat -d -v time > logcat.txt 2>&1

:restore
if defined KEEPON !A! shell settings put system screen_off_timeout !OLDTO! >nul 2>&1
if defined FAILED goto end
if not exist "%NAME%.pftrace" (
  echo.
  echo The trace didn't come back from the watch. Send perfetto_log.txt and info.txt from this folder.
  goto end
)
set "ZIP=%NAME%.zip"
if exist "%ZIP%" del "%ZIP%"
tar -a -c -f "%ZIP%" "%NAME%.pftrace" info.txt gfxinfo.txt meminfo_runtime.txt threads.txt perfetto_log.txt logcat.txt >nul 2>&1
echo.
if exist "%ZIP%" (
  echo Done. Send %ZIP% from this folder: it's picked out in the window that opens.
  explorer /select,"!CD!\%ZIP%"
) else (
  echo Done. Send %NAME%.pftrace and the .txt files from this folder.
  explorer "!CD!"
)
goto end

:missing
echo trace_config.pbtx or trace_config_basic.pbtx isn't next to this file. Unzip the whole
echo tools\trace folder first, then run record_trace.bat from there.

:end
echo.
pause
