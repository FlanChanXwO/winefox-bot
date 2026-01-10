@echo off
setlocal

:: ========================================================
::               WINEFOX BOT (独立窗口版)
:: ========================================================

:: --- 配置 ---
set "APP_NAME=winefox-bot-shiro"
set "DAEMON_SCRIPT=start-daemon.bat"
:: 这个标题用于识别和关闭窗口
set "WINDOW_TITLE=WineFox-Bot-Console-Window"

:: --- 路径处理 ---
set "BASE_DIR=%~dp0"
if "%BASE_DIR:~-1%"=="\" set "BASE_DIR=%BASE_DIR:~0,-1%"

:: --- 1. 参数检查 (修复点) ---
:: 如果第一个参数为空，直接跳转到 Usage 显示帮助
set "command=%~1"
if "%command%"=="" goto usage

:: --- 2. 命令分发 ---
if /I "%command%"=="start" goto start
if /I "%command%"=="stop" goto stop
if /I "%command%"=="restart" goto restart
if /I "%command%"=="status" goto status

:: 如果输入了未知命令
echo [ERROR] Unknown command: %command%
goto usage


:: ========================================================
::                      COMMANDS
:: ========================================================

:start
tasklist /FI "WINDOWTITLE eq %WINDOW_TITLE%*" 2>nul | find /I "cmd.exe" >nul
if %errorlevel% equ 0 (
    echo [INFO] The bot is already running in another window.
    goto :eof
)

echo [INFO] Launching %APP_NAME% in a new window...
start "%WINDOW_TITLE%" cmd /k "%BASE_DIR%\%DAEMON_SCRIPT%"
echo [SUCCESS] Window launched.
goto :eof


:stop
echo [INFO] Stopping %APP_NAME%...
taskkill /F /T /FI "WINDOWTITLE eq %WINDOW_TITLE%*" /IM cmd.exe >nul 2>&1
echo [SUCCESS] Stop command sent.
goto :eof


:status
echo.
echo Checking status for: %WINDOW_TITLE%
echo ----------------------------------------
tasklist /V /FI "WINDOWTITLE eq %WINDOW_TITLE%*" | find /I "cmd.exe" >nul
if %errorlevel% equ 0 (
    echo [STATUS] RUNNING - The console window is open.
) else (
    echo [STATUS] STOPPED - No console window found.
)
echo.
goto :eof


:restart
echo [INFO] Restarting...
call :stop
timeout /t 2 /nobreak > nul
call :start
goto :eof


:usage
echo.
echo ========================================================
echo   WineFox Bot Control Script (Windows)
echo ========================================================
echo.
echo Usage: %~n0 [command]
echo.
echo Commands:
echo   start    - Start the bot (New Window)
echo   stop     - Stop the bot
echo   restart  - Restart the bot
echo   status   - Check status
echo.
goto :eof