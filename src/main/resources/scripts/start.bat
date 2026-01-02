@echo off
setlocal enabledelayedexpansion

REM --- Configuration ---
set "JAR_FILE=winefox-bot.jar"
set "RESTART_CODE=5"
set "LOGS_DIR=logs"
set "BOT_WINDOW_TITLE=WineFoxBot Process"

REM --- Get the script's own directory ---
set "SCRIPT_DIR=%~dp0"

REM --- Create the log directory if it doesn't exist ---
if not exist "%SCRIPT_DIR%%LOGS_DIR%" (
    echo Creating log directory: %LOGS_DIR%
    mkdir "%SCRIPT_DIR%%LOGS_DIR%"
)

REM --- Generate a timestamped log file name ---
set "DATETIME_STR=%date:~0,4%-%date:~5,2%-%date:~8,2%_%time:~0,2%-%time:~3,2%-%time:~6,2%"
set "DATETIME_STR=%DATETIME_STR: =0%"
set "LOG_FILE=%SCRIPT_DIR%%LOGS_DIR%\%DATETIME_STR%.log"

REM --- Console welcome message ---
echo =================================================================
echo.
echo  WineFoxBot Starter Script (v2.1 - Robust)
echo.
echo  - JAR File: %JAR_FILE%
echo  - Log file for this session will be: %LOG_FILE%
echo.
echo =================================================================
echo.
echo A new window will open for the bot process.
echo All output from the bot will be redirected to the log file.
echo Press Ctrl+C in the NEW window to stop the bot.
echo.

:start
REM --- Get current time for logging ---
set "CURRENT_TIME=%date% %time%"

REM --- Log the start of an attempt ---
(
    echo.
    echo ======================================================
    echo           SCRIPT EXECUTION STARTED AT %CURRENT_TIME%
    echo ======================================================
    echo.
    echo Starting %JAR_FILE%...
    echo Command: start /wait "%BOT_WINDOW_TITLE%" java -jar "%SCRIPT_DIR%%JAR_FILE%"
    echo.
) >> "%LOG_FILE%"

REM --- Start the Java process in a new window and wait for it to exit ---
start "%BOT_WINDOW_TITLE%" /wait java -jar "%SCRIPT_DIR%%JAR_FILE%"

REM --- Capture the exit code immediately after the process terminates ---
set "EXIT_CODE=%ERRORLEVEL%"
set "CURRENT_TIME=%date% %time%"

REM --- Log the outcome ---
(
    echo.
    echo ==================== PROCESS EXITED ====================
    echo.
    echo  Java process exited at: !CURRENT_TIME!
    echo  The captured EXIT_CODE is: [!EXIT_CODE!]
    echo  The expected RESTART_CODE is: [!RESTART_CODE!]
    echo.

    REM --- Check if the exit code matches the restart code ---
    if "!EXIT_CODE!" == "!RESTART_CODE!" (
        echo  [INFO] Exit code matches. Restarting in 3 seconds...
        echo ========================================================
        timeout /t 3 /nobreak >nul
        goto start
    ) else (
        echo  [INFO] Exit code does not match. The script will now exit.
        echo ========================================================
    )
) >> "%LOG_FILE%"

REM --- Script finished message ---
echo.
echo =================================================================
echo.
echo  The bot process has finished and will not be restarted.
echo  - Final exit code was: !EXIT_CODE!
echo  - Detailed log has been saved to:
echo    %LOG_FILE%
echo.
echo =================================================================
pause
