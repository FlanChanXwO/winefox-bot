#!/bin/bash

# ================== 配置区 ==================
# 请根据你的实际情况修改这些变量

# Java 启动参数
JAVA_OPTS="-Xms512m -Xmx1024m"

# 目标 JAR 文件的相对路径 (相对于项目根目录)
JAR_PATH="target/winefox-bot.jar"

# 更新时下载的临时 JAR 文件的相对路径
TEMP_JAR_PATH="target/update-temp"

# 触发更新的特定退出码
UPDATE_EXIT_CODE=5

# ==========================================

# 获取脚本所在的目录, 并切换到其上一级目录 (项目根目录)
# 这是确保所有相对路径 (JAR, config, update file) 正确的关键
cd "$(dirname "$0")/.."
echo "[SETUP] Script is now running in directory: $(pwd)"

# 无限循环
while true; do
    echo ""
    echo "======================================================="
    echo "Starting Application..."
    echo "JAR Path: \"$JAR_PATH\""
    echo "Timestamp: $(date)"
    echo "======================================================="
    echo ""

    # 运行 Java 程序
    # Spring Boot 会自动加载与 JAR 同目录的 application.yml
    java $JAVA_OPTS -jar "$JAR_PATH"
    EXIT_CODE=$?

    echo ""
    echo "======================================================="
    echo "Application exited with code: $EXIT_CODE"
    echo "======================================================="
    echo ""

    # 检查退出码是否为我们约定的“重启更新”码
    if [ $EXIT_CODE -eq $UPDATE_EXIT_CODE ]; then
        # ---- 更新流程开始 ----
        echo "[UPDATE] Update exit code detected. Starting update process..."

        # 等待一小会儿，确保文件句柄被操作系统完全释放
        sleep 2

        if [ -f "$TEMP_JAR_PATH" ]; then
            echo "[UPDATE] Found update file: '$TEMP_JAR_PATH'."
            echo "[UPDATE] Attempting to replace '$JAR_PATH'..."

            # 使用 mv -f 命令来强制覆盖旧文件
            mv -f "$TEMP_JAR_PATH" "$JAR_PATH"

            if [ $? -eq 0 ]; then
                echo "[SUCCESS] Update complete! '$JAR_PATH' has been successfully updated."
            else
                echo "[ERROR] Update failed: Could not replace the JAR file. Check file permissions."
                echo "[INFO] Restarting with the old version."
            fi
        else
            echo "[ERROR] Update failed: Temporary file '$TEMP_JAR_PATH' not found!"
            echo "[INFO] Aborting update and restarting with the old version."
        fi
    else
        echo "[INFO] Normal exit or crash detected (Code: $EXIT_CODE). Restarting application..."
    fi

    echo "[INFO] Looping back to restart the application in 3 seconds..."
    sleep 3
done
