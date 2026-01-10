#!/bin/bash

# ===================== 配置区 =====================
# Java 启动参数
JAVA_OPTS="-Xms400m -Xmx800m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=./logs/heapdump.hprof -Djava.io.tmpdir=./tmp -Dfile.encoding=UTF-8"

# 目标 JAR 文件的相对路径
JAR_PATH="winefox-bot.jar"
# 更新时下载的临时 JAR 文件的相对路径
TEMP_JAR_PATH="update-temp.jar"
TEMP_LIB_PATH="update-lib.zip"
# 触发更新的特定退出码
UPDATE_EXIT_CODE=5
# ==================================================

# 获取脚本所在的目录
cd "$(dirname "$0")"
echo "[SETUP] Script is now running in directory: $(pwd)"

mkdir -p ./logs
mkdir -p ./tmp

while true; do
    echo
    echo "======================================================="
    echo "Starting Application..."
    echo "JAR Path: \"$JAR_PATH\""
    echo "Timestamp: $(date '+%Y-%m-%d %H:%M:%S')"
    echo "======================================================="
    echo

    # 【修复 1】启动前检查 JAR 文件是否存在
    if [ ! -f "$JAR_PATH" ]; then
        echo "[FATAL ERROR] JAR file '$JAR_PATH' not found!"
        echo "Please ensure the jar file is named correctly and placed in this directory."
        echo "Exiting to prevent infinite restart loop."
        exit 1
    fi

    # 运行 Java 程序
    java $JAVA_OPTS -jar "$JAR_PATH"
    EXIT_CODE=$?

    echo
    echo "======================================================="
    echo "Application exited with code: $EXIT_CODE"
    echo "Timestamp: $(date '+%Y-%m-%d %H:%M:%S')"
    echo "======================================================="
    echo

    # 【修复 2】如果是正常退出 (0)，则停止循环
    if [ $EXIT_CODE -eq 0 ]; then
        echo "[INFO] Application exited normally (Code 0). Stopping daemon."
        break
    fi

    # 检查退出码是否为我们约定的“重启更新”码
    if [ $EXIT_CODE -eq $UPDATE_EXIT_CODE ]; then
        # ---- 更新流程 (保持原样) ----
        echo "[UPDATE] Update exit code detected. Starting update process..."
        sleep 2

        if [ ! -f "$TEMP_JAR_PATH" ] && [ ! -f "$TEMP_LIB_PATH" ]; then
            echo "[ERROR] Update exit code 5 received but no update files found!"
            echo "[INFO] Restarting with the current version."
        fi

        # 处理 JAR 更新
        if [ -f "$TEMP_JAR_PATH" ]; then
            echo "[UPDATE] Found update file: '$TEMP_JAR_PATH'."
            echo "[UPDATE] Attempting to replace '$JAR_PATH'..."
            if mv -f "$TEMP_JAR_PATH" "$JAR_PATH"; then
                echo "[SUCCESS] Update complete! '$JAR_PATH' has been successfully updated."
            else
                echo "[ERROR] Update failed: Could not replace the JAR file. Check permissions."
            fi
        fi

        # 处理 Lib 更新 (保持原样)
        if [ -f "$TEMP_LIB_PATH" ]; then
             if command -v unzip >/dev/null 2>&1; then
                unzip -o "$TEMP_LIB_PATH" && rm -f "$TEMP_LIB_PATH"
             elif command -v jar >/dev/null 2>&1; then
                jar -xf "$TEMP_LIB_PATH" && rm -f "$TEMP_LIB_PATH"
             fi
        fi

    else
        echo "[WARN] Abnormal exit or crash detected (Code: $EXIT_CODE). Restarting application..."
    fi

    echo "[INFO] Looping back to restart the application in 3 seconds..."
    sleep 3
done
