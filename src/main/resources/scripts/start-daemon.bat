@echo off
setlocal enabledelayedexpansion

:: ===================== 配置区 =====================
set "JAVA_OPTS=-Xms400m -Xmx800m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=.\logs\heapdump.hprof -Djava.io.tmpdir=.\tmp -Dfile.encoding=UTF-8"
:: ====================================================

set "JAR_PATH=winefox-bot.jar"
set "TEMP_JAR_PATH=update-temp.jar"
set "TEMP_LIB_PATH=update-lib.zip"
:: [新增] 定义资源包路径
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

:: 检查 JAR 是否存在
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

:: 正常退出检测 (Code 0)
if "!EXIT_CODE!" == "0" (
    echo [INFO] Application exited normally. Stopping daemon.
    goto :eof
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
    :: 使用 tar 解压
    tar -xf "%TEMP_LIB_PATH%"
    if !errorlevel! == 0 (
        echo [SUCCESS] Libs extracted. Deleting zip...
        del "%TEMP_LIB_PATH%"
    ) else (
        echo [ERROR] Failed to extract lib.zip
    )
)

:: [新增] 处理资源文件更新逻辑
if exist "%TEMP_RES_PATH%" (
    echo [UPDATE] Found Resources update. Extracting...
    :: 使用 tar 解压覆盖
    tar -xf "%TEMP_RES_PATH%"
    if !errorlevel! == 0 (
        echo [SUCCESS] Resources extracted. Deleting zip...
        :: [关键] 只有解压成功才删除文件，防止文件堆积
        del "%TEMP_RES_PATH%"
    ) else (
        echo [ERROR] Failed to extract resources.zip
    )
)

:restart_loop
echo [INFO] Looping back to restart the application in 3 seconds...
timeout /t 3 /nobreak > nul
goto main_loop

endlocal
