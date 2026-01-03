@echo off
setlocal

:: ========================================================
::               WINEFOX BOT (独立窗口版)
:: ========================================================

:: --- 配置 ---
set "APP_NAME=winefox-bot-shiro"
set "DAEMON_SCRIPT=start-daemon.bat"
:: 这个标题非常重要，用于识别和关闭窗口
set "WINDOW_TITLE=WineFox-Bot-Console-Window"

:: --- 路径处理 (使用绝对路径) ---
set "BASE_DIR=%~dp0"
:: 去掉末尾的反斜杠
if "%BASE_DIR:~-1%"=="\" set "BASE_DIR=%BASE_DIR:~0,-1%"

:: --- 命令分发 ---
set "command=%~1"
if /I "%command%"=="start" goto start
if /I "%command%"=="stop" goto stop
if /I "%command%"=="restart" goto restart
if /I "%command%"=="status" goto status

echo.
echo Usage: %~n0 [start|stop|restart|status]
echo.
goto :eof


:: ========================================================
::                      COMMANDS
:: ========================================================

:start
:: 1. 检查是否已经在运行
tasklist /FI "WINDOWTITLE eq %WINDOW_TITLE%*" 2>nul | find /I "cmd.exe" >nul
if %errorlevel% equ 0 (
    echo [INFO] The bot is already running in another window.
    goto :eof
)

echo [INFO] Launching %APP_NAME% in a new window...

:: 2. 启动新窗口
:: cmd /k 意思是 "执行完命令后保持窗口打开"，这样如果报错你能看到
:: "start-daemon.bat" 将在新窗口中运行
start "%WINDOW_TITLE%" cmd /k "%BASE_DIR%\%DAEMON_SCRIPT%"

echo [SUCCESS] Window launched. Check the new window for logs.
goto :eof


:stop
echo [INFO] Stopping %APP_NAME%...

:: 1. 通过窗口标题关闭 CMD 窗口
taskkill /F /FI "WINDOWTITLE eq %WINDOW_TITLE%*" /IM cmd.exe >nul 2>&1

:: 2. 确保关闭所有关联的 Java 进程 (如果有遗留)
:: 注意：这里假设你的机器上主要跑这一个 Java 机器人。
:: 如果有多个 Java 程序，请谨慎，或者只依赖上面的窗口关闭。
taskkill /F /IM java.exe /FI "WINDOWTITLE eq %WINDOW_TITLE%*" >nul 2>&1

echo [SUCCESS] Stop command sent.
goto :eof


:status
echo.
echo Checking status for: %WINDOW_TITLE%
echo ----------------------------------------

tasklist /V /FI "WINDOWTITLE eq %WINDOW_TITLE%*" | find /I "cmd.exe" >nul
if %errorlevel% equ 0 (
    echo [STATUS] RUNNING - The console window is open.

    :: 尝试查找 Java 进程
    tasklist /FI "IMAGENAME eq java.exe" 2>nul | find /I "java.exe" >nul
    if %errorlevel% equ 0 (
        echo [STATUS] Java process is also active.
    ) else (
        echo [WARN]   Console is open, but Java might not be running.
    )
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