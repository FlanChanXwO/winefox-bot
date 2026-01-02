@echo off
setlocal enabledelayedexpansion

:: ================== 配置区 ==================
:: 请根据你的实际情况修改这些变量

:: JAVA_HOME 路径 (可选, 如果你的系统环境变量已正确配置则无需修改)
:: set "JAVA_HOME=C:\path\to\your\jdk"
:: set "PATH=%JAVA_HOME%\bin;%PATH%"

:: Java 启动参数
set "JAVA_OPTS=-Xms512m -Xmx1024m"

:: 目标 JAR 文件的相对路径 (相对于项目根目录)
set "JAR_PATH=target\winefox-bot.jar"

:: 更新时下载的临时 JAR 文件的相对路径
set "TEMP_JAR_PATH=target\update-temp"

:: 触发更新的特定退出码
set "UPDATE_EXIT_CODE=5"

:: ==========================================

:: 切换到脚本所在目录的上一级目录 (即项目根目录)
:: 这是确保所有相对路径 (JAR, config, update file) 正确的关键
cd /d %~dp0..
echo [SETUP] Script is now running in directory: %cd%

:main_loop
echo.
echo =======================================================
echo Starting Application...
echo JAR Path: "%JAR_PATH%"
echo Timestamp: %date% %time%
echo =======================================================
echo.

:: 运行 Java 程序
:: Spring Boot 会自动加载与 JAR 同目录的 application.yml
java %JAVA_OPTS% -jar "%JAR_PATH%"
set "EXIT_CODE=!errorlevel!"

echo.
echo =======================================================
echo Application exited with code: !EXIT_CODE!
echo =======================================================
echo.

:: 检查退出码是否为我们约定的“重启更新”码
if not "!EXIT_CODE!" == "!UPDATE_EXIT_CODE!" goto normal_restart

:: ---- 更新流程开始 ----
echo [UPDATE] Update exit code detected. Starting update process...

:: 等待一小会儿，确保文件句柄被操作系统完全释放
timeout /t 2 /nobreak > nul

:: 检查临时更新文件是否存在
if not exist "%TEMP_JAR_PATH%" goto update_failed_no_file

echo [UPDATE] Found update file: '%TEMP_JAR_PATH%'.
echo [UPDATE] Attempting to replace '%JAR_PATH%'...

:: 使用 MOVE /Y 命令来强制覆盖旧文件，这是最可靠的方式
move /Y "%TEMP_JAR_PATH%" "%JAR_PATH%"

:: 检查 move 命令是否成功
if not !errorlevel! == 0 goto update_failed_move
goto update_success

:: ---- 更新流程中的不同结果分支 ----
:update_failed_no_file
echo [ERROR] Update failed: Temporary file '%TEMP_JAR_PATH%' not found!
echo [INFO] Aborting update and restarting with the old version.
goto restart_loop

:update_failed_move
echo [ERROR] Update failed: Could not replace the JAR file using 'move' command. Check file permissions.
echo [INFO] Restarting with the old version.
goto restart_loop

:update_success
echo [SUCCESS] Update complete! '%JAR_PATH%' has been successfully updated.
goto restart_loop

:: ---- 重启流程 ----
:normal_restart
echo [INFO] Normal exit or crash detected (Code: !EXIT_CODE!). Restarting application...
goto restart_loop

:restart_loop
echo [INFO] Looping back to restart the application in 3 seconds...
timeout /t 3 /nobreak > nul
goto main_loop

endlocal
