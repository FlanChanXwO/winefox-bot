@echo off
setlocal enabledelayedexpansion

:: ===================== 防重锁配置 =====================
set "LOCK_FILE=daemon.lock"

:: 尝试锁定文件 (Stream 9)
:: 如果锁定失败 (||)，说明已经有实例在运行
(
    call :main_logic
) 9> "%LOCK_FILE%" || (
    color 4f
    echo.
    echo ==========================================
    echo [ERROR] 程序已经在运行了！(Found Lock File)
    echo ==========================================
    echo 请检查任务栏是否已有 CMD 窗口，或在任务管理器中结束 cmd.exe。
    echo.
    pause
    exit /b 1
)
:: 运行结束退出
goto :eof
:: ==========================================================


:: ===================== 原有逻辑封装在标签内 =====================
:main_logic
:: --- 原有配置区 ---
set "JAVA_OPTS=-Xms400m -Xmx800m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=.\logs\heapdump.hprof -Djava.io.tmpdir=.\tmp -Dfile.encoding=UTF-8 -Dspring.profiles.active=prod"
set "JAR_PATH=winefox-bot.jar"
set "TEMP_JAR_PATH=update-temp.jar"
set "TEMP_LIB_PATH=update-lib.zip"
set "TEMP_RES_PATH=update-resources.zip"
set "UPDATE_EXIT_CODE=5"

:: 切换目录
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

if not exist "%JAR_PATH%" (
    echo [FATAL ERROR] JAR file "%JAR_PATH%" not found!
    echo Please rename your jar to "%JAR_PATH%" or update the script.
    echo Press any key to exit...
    pause
    exit /b 1
)

:: 运行 Java 程序
java %JAVA_OPTS% -jar "%JAR_PATH%"
set "EXIT_CODE=!errorlevel!"

echo.
echo =======================================================
echo Application exited with code: !EXIT_CODE!
echo Timestamp: %date% %time%
echo =======================================================
echo.

:: 正常退出 (Code 0)
if "!EXIT_CODE!" == "0" (
    echo [INFO] Application exited normally. Stopping daemon.
    :: 注意：在 call 内部要用 exit /b 来退出函数，而不是关闭窗口
    exit /b 0
)

:: 检查更新码
if not "!EXIT_CODE!" == "!UPDATE_EXIT_CODE!" (
    echo [WARN] Abnormal exit detected [Code: !EXIT_CODE!]. Restarting application...
    goto restart_loop
)

:: ---- 更新流程 ----
echo [UPDATE] Update exit code detected. Starting update process...
timeout /t 2 /nobreak > nul

if exist "%TEMP_JAR_PATH%" (
    echo [UPDATE] Found JAR update. Replacing...
    move /Y "%TEMP_JAR_PATH%" "%JAR_PATH%" > nul
)

if exist "%TEMP_LIB_PATH%" (
    echo [UPDATE] Found Lib update. Extracting...
    tar -xf "%TEMP_LIB_PATH%"
    if !errorlevel! == 0 (
        echo [SUCCESS] Libs extracted. Deleting zip...
        del "%TEMP_LIB_PATH%"
    ) else (
        echo [ERROR] Failed to extract lib.zip
    )
)

if exist "%TEMP_RES_PATH%" (
    echo [UPDATE] Found Resources update. Extracting...
    tar -xf "%TEMP_RES_PATH%"
    if !errorlevel! == 0 (
        echo [SUCCESS] Resources extracted. Deleting zip...
        del "%TEMP_RES_PATH%"
    ) else (
        echo [ERROR] Failed to extract resources.zip
    )
)

:restart_loop
echo [INFO] Looping back to restart the application in 3 seconds...
timeout /t 3 /nobreak > nul
goto main_loop

:: 结束标签
exit /b 0