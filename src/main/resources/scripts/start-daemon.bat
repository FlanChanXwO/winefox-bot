@echo off
setlocal enabledelayedexpansion

:: ===================== 配置区 =====================
:: Java 启动参数
set "JAVA_OPTS=-Xms2048m -Xmx2048m"
set "JAVA_OPTS=%JAVA_OPTS% -XX:+UseG1GC"
set "JAVA_OPTS=%JAVA_OPTS% -XX:MaxGCPauseMillis=200"
set "JAVA_OPTS=%JAVA_OPTS% -XX:+HeapDumpOnOutOfMemoryError"
set "JAVA_OPTS=%JAVA_OPTS% -XX:HeapDumpPath=.\logs\heapdump.hprof"
set "JAVA_OPTS=%JAVA_OPTS% -Djava.io.tmpdir=.\tmp"
set "JAVA_OPTS=%JAVA_OPTS% -Dfile.encoding=UTF-8"
:: ====================================================

:: 目标 JAR 文件的相对路径
set "JAR_PATH=winefox-bot.jar"
:: 更新时下载的临时 JAR 文件的相对路径
set "TEMP_JAR_PATH=update-temp"
:: 触发更新的特定退出码
set "UPDATE_EXIT_CODE=5"

:: 切换到脚本文件所在的目录
cd /d %~dp0
echo [SETUP] Script is now running in directory: %cd%

if not exist ".\logs" mkdir ".\logs"
if not exist ".\tmp" mkdir ".\tmp"

:main_loop
echo.
echo =======================================================
echo Starting Application...
echo JAR Path: "%JAR_PATH%"
echo Timestamp: %date% %time%
echo =======================================================
echo.

:: 运行 Java 程序
java %JAVA_OPTS% -jar "%JAR_PATH%"
set "EXIT_CODE=!errorlevel!"

echo.
echo =======================================================
echo Application exited with code: !EXIT_CODE!
echo Timestamp: %date% %time%
echo =======================================================
echo.

:: 检查退出码是否为我们约定的“重启更新”码
if not "!EXIT_CODE!" == "!UPDATE_EXIT_CODE!" (
    :: ！！！修复点：将圆括号 () 改为方括号 []，防止破坏 IF 结构 ！！！
    echo [INFO] Normal exit or crash detected [Code: !EXIT_CODE!]. Restarting application...
    goto restart_loop
)

:: ---- 更新流程 ----
echo [UPDATE] Update exit code detected. Starting update process...
timeout /t 2 /nobreak > nul

if not exist "%TEMP_JAR_PATH%" (
    echo [ERROR] Update failed: Temporary update file '%TEMP_JAR_PATH%' not found!
    echo [INFO] Aborting update and restarting with the old version.
    goto restart_loop
)

echo [UPDATE] Found update file: '%TEMP_JAR_PATH%'.
echo [UPDATE] Attempting to replace '%JAR_PATH%'...

:: 使用 MOVE /Y 命令来强制覆盖旧文件
move /Y "%TEMP_JAR_PATH%" "%JAR_PATH%" > nul

if !errorlevel! == 0 (
    echo [SUCCESS] Update complete! '%JAR_PATH%' has been successfully updated.
) else (
    echo [ERROR] Update failed: Could not replace the JAR file. Check file permissions or if it's locked.
    echo [INFO] Restarting with the old version.
)

:restart_loop
echo [INFO] Looping back to restart the application in 3 seconds...
timeout /t 3 /nobreak > nul
goto main_loop

endlocal