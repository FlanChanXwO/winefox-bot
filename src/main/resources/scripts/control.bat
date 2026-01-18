@echo off
setlocal

:: ========================================================
::               WINEFOX BOT (Windows)
:: ========================================================

set "APP_NAME=winefox-bot-shiro"
set "DAEMON_SCRIPT=start-daemon.bat"
set "WINDOW_TITLE=WineFox-Bot-Console"
set "BASE_DIR=%~dp0"
if "%BASE_DIR:~-1%"=="\" set "BASE_DIR=%BASE_DIR:~0,-1%"

:: 参数检查
set "command=%~1"
if "%command%"=="" goto usage

if /I "%command%"=="start" goto start
if /I "%command%"=="stop" goto stop
if /I "%command%"=="restart" goto restart
if /I "%command%"=="status" goto status
if /I "%command%"=="logs" goto logs

echo [ERROR] Unknown command: %command%
goto usage

:: ================= COMMANDS =================

:start
tasklist /FI "WINDOWTITLE eq %WINDOW_TITLE%*" | find /I "cmd.exe" >nul
if %errorlevel% equ 0 (
    echo [INFO] Bot is already running.
    goto :eof
)
echo [INFO] Launching %APP_NAME% in a new window...
start "%WINDOW_TITLE%" cmd /k "%BASE_DIR%\%DAEMON_SCRIPT%"
goto :eof

:stop
echo [INFO] Stopping...
taskkill /F /FI "WINDOWTITLE eq %WINDOW_TITLE%*" /IM cmd.exe >nul 2>&1
echo [SUCCESS] Stop signal sent.
goto :eof

:restart
call :stop
timeout /t 2 /nobreak > nul
call :start
goto :eof

:status
tasklist /V /FI "WINDOWTITLE eq %WINDOW_TITLE%*" | find /I "cmd.exe" >nul
if %errorlevel% equ 0 (
    echo [STATUS] RUNNING - Check the "%WINDOW_TITLE%" window.
) else (
    echo [STATUS] STOPPED
)
goto :eof

:logs
echo.
echo [INFO] Logs are displayed in the separate "%WINDOW_TITLE%" window.
echo [INFO] Please switch to that window to view real-time output.
goto :eof

:usage
echo.
echo Usage: %~n0 [start|stop|restart|status|logs]
echo.
goto :eof
