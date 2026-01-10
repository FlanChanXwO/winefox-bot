@echo off
setlocal enabledelayedexpansion

:: ===================== 配置区 =====================
:: Java 启动参数
set "JAVA_OPTS=-Xms400m -Xmx800m"
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

if not exist "%TEMP_JAR_PATH%" if not exist "%TEMP_LIB_PATH%" (
    echo [ERROR] Update exit code 5 received but no update files found!
    echo [INFO] Restarting with the current version.
    goto restart_loop
)

if exist "%TEMP_JAR_PATH%" (
    echo [UPDATE] Found JAR update: '%TEMP_JAR_PATH%'.
    echo [UPDATE] Attempting to replace '%JAR_PATH%'...
    move /Y "%TEMP_JAR_PATH%" "%JAR_PATH%" > nul
    if !errorlevel! == 0 (
        echo [SUCCESS] JAR successfully updated.
    ) else (
        echo [ERROR] Update failed: Could not replace the JAR file. Check file permissions or if it's locked.
    )
)

if exist "%TEMP_LIB_PATH%" (
    echo [UPDATE] Found Library update: '%TEMP_LIB_PATH%'.
    echo [UPDATE] Extracting libraries...

    :: Try tar first (Win10+)
    tar -xf "%TEMP_LIB_PATH%"
    if !errorlevel! == 0 (
        echo [SUCCESS] Libraries extracted successfully.
        del "%TEMP_LIB_PATH%"
    ) else (
        echo [WARN] 'tar' failed or not found. Trying PowerShell...
        powershell -command "Expand-Archive -Force '%TEMP_LIB_PATH%' ."
        if !errorlevel! == 0 (
             echo [SUCCESS] Libraries extracted successfully via PowerShell.
             del "%TEMP_LIB_PATH%"
        ) else (
             echo [ERROR] Failed to extract libraries! Check if files are locked.
        )
    )
)

:restart_loop
echo [INFO] Looping back to restart the application in 3 seconds...
timeout /t 3 /nobreak > nul
goto main_loop

endlocal