@echo off
setlocal enabledelayedexpansion

:: ===================== 配置区 =====================
:: 切换到脚本所在目录的上一级目录 (即项目根目录)
cd /d %~dp0..

:: 应用名称，用于日志、PID文件和进程识别
set "APP_NAME=winefox-bot-shiro"

:: 实际执行应用循环和更新逻辑的脚本文件名
:: 这应该是你原来的 start.bat (File 2), 建议重命名
set "DAEMON_SCRIPT_NAME=.\start-daemon.bat"

:: --- 日志与PID配置 ---
set "LOG_DIR=.\logs"
set "PID_FILE=.\%APP_NAME%.pid"

:: 为后台进程设置一个唯一的窗口标题，用于识别
set "PROCESS_TITLE=%APP_NAME%-Daemon"
:: ==================================================


:: --- 脚本自检 ---
if not exist "%DAEMON_SCRIPT_NAME%" (
    echo [ERROR] 守护脚本 '%DAEMON_SCRIPT_NAME%' 不存在.
    goto :eof
)

:: 确保日志目录存在
if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"


:: 函数: 获取PID
:get_pid
set "pid="
if exist "%PID_FILE%" (
    set /p pid=<"%PID_FILE%"
    if defined pid (
        :: 检查该PID的进程是否存在
        tasklist /FI "PID eq !pid!" 2>nul | find /I "!pid!" >nul
        if !errorlevel! neq 0 (
            echo [WARN] 发现无效的PID文件，已自动清理.
            del "%PID_FILE%"
            set "pid="
        )
    )
)
goto :eof


:: 函数: 启动
:start
call :get_pid
if defined pid (
    echo %APP_NAME% 已经在后台运行 (主进程 PID: !pid!).
    goto :eof
)

echo 正在后台启动 %APP_NAME%...

:: 日志文件按天归档 (格式 YYYY-MM-DD)
set "datestr=%date:~0,4%-%date:~5,2%-%date:~8,2%"
set "logfile=%LOG_DIR%\%datestr%.log"

:: 使用 start /B 在后台启动守护脚本，并将所有输出重定向
:: %PROCESS_TITLE% 是关键，我们用它来识别进程
start "%PROCESS_TITLE%" /B cmd /c "%DAEMON_SCRIPT_NAME% >> ""%logfile%"" 2>&1"

:: 延迟一小会儿，等待进程创建
timeout /t 2 /nobreak > nul

:: 通过窗口标题找到新启动的后台进程的PID
set "new_pid="
for /f "tokens=2" %%i in ('tasklist /V /FI "WINDOWTITLE eq %PROCESS_TITLE%" /FO CSV ^| find /I "%PROCESS_TITLE%"') do (
    set "new_pid=%%~i"
)

if defined new_pid (
    echo !new_pid! > "%PID_FILE%"
    echo %APP_NAME% 启动成功 (主进程 PID: !new_pid!).
    echo 所有日志将写入: %logfile%
) else (
    echo [ERROR] 启动失败: 未能找到后台进程.
    echo 请检查日志文件以获取详细错误信息: %logfile%
    if exist "%logfile%" (
        echo --- 最近的日志内容 ---
        powershell -Command "Get-Content '%logfile%' -Tail 20"
    )
)
goto :eof


:: 函数: 停止
:stop
call :get_pid
if not defined pid (
    echo %APP_NAME% 未在运行.
    goto :eof
)

echo 正在停止 %APP_NAME% (主进程 PID: %pid%)...

:: 首先，杀死主守护进程 (cmd.exe)
taskkill /PID %pid% /F > nul

:: 然后，杀死由主守护进程启动的所有子进程 (java.exe)
:: /T 参数会终止该进程及其所有子进程
taskkill /PID %pid% /T /F > nul

del "%PID_FILE%" > nul 2>&1
echo %APP_NAME% 已成功停止.
goto :eof


:: 函数: 状态
:status
call :get_pid
if defined pid (
    echo %APP_NAME% 主守护进程正在运行 (PID: %pid%).
    set "java_pid_found="
    for /f "tokens=2" %%i in ('wmic process where "ParentProcessId=%pid% AND Name='java.exe'" get ProcessId /value 2^>nul ^| find "Id"') do (
        for /f "tokens=2 delims==" %%j in ("%%i") do (
            echo  -^> Java 应用进程正在运行 (PID: %%j).
            set "java_pid_found=true"
        )
    )
    if not defined java_pid_found (
        echo  -^> [WARN] 未找到关联的Java进程, 可能正在重启或已崩溃.
    )
    set "datestr=%date:~0,4%-%date:~5,2%-%date:~8,2%"
    echo 今日日志: %LOG_DIR%\%datestr%.log
) else (
    echo %APP_NAME% 未运行.
)
goto :eof


:: 函数: 重启
:restart
echo 正在重启 %APP_NAME%...
call :stop
timeout /t 2 /nobreak > nul
call :start
goto :eof


:: 函数: 查看日志
:logs
set "datestr=%date:~0,4%-%date:~5,2%-%date:~8,2%"
set "logfile=%LOG_DIR%\%datestr%.log"

if not exist "%logfile%" (
    echo 今日日志文件不存在: %logfile%
    goto :eof
)

if /I "%2" == "-f" (
    echo 正在实时跟踪日志 (按 Ctrl+C 退出)...
    powershell -Command "Get-Content '%logfile%' -Wait -Tail 50"
) else (
    echo 使用 more 命令分页查看日志 (按 Q 退出)...
    echo.
    more < "%logfile%"
)
goto :eof


:: --- 主逻辑入口 ---
if /I "%1" == "start"   goto :start
if /I "%1" == "stop"    goto :stop
if /I "%1" == "restart" goto :restart
if /I "%1" == "status"  goto :status
if /I "%1" == "logs"    goto :logs

:usage
echo.
echo 一个健壮的Windows应用控制脚本
echo 用法: %~n0 {start^|stop^|restart^|status^|logs [-f]}
echo.
goto :eof
