#!/bin/bash

# ===================== 配置区 =====================
# Java 启动参数
# 推荐在8G内存服务器上设置为2G
JAVA_OPTS="-Xms2048m -Xmx2048m"
# 使用G1垃圾收集器
JAVA_OPTS="${JAVA_OPTS} -XX:+UseG1GC"
# 期望GC最大停顿时间为200毫秒
JAVA_OPTS="${JAVA_OPTS} -XX:MaxGCPauseMillis=200"
# 发生内存溢出时自动生成堆转储文件
JAVA_OPTS="${JAVA_OPTS} -XX:+HeapDumpOnOutOfMemoryError"
# 指定堆转储文件的存放路径 (建议放在logs目录下)
JAVA_OPTS="${JAVA_OPTS} -XX:HeapDumpPath=./logs/heapdump.hprof"
# 指定Java应用的临时文件目录 (重要！)
JAVA_OPTS="${JAVA_OPTS} -Djava.io.tmpdir=./tmp"

# 目标 JAR 文件的相对路径
JAR_PATH="winefox-bot.jar"

# 更新时下载的临时 JAR 文件的相对路径
TEMP_JAR_PATH="update-temp"

# 触发更新的特定退出码
UPDATE_EXIT_CODE=5
# ==================================================

# 获取脚本所在的目录, 并切换到其上一级目录 (项目根目录)
cd "$(dirname "$0")/.."
echo "[SETUP] Script is now running in directory: $(pwd)"

# 确保日志和临时目录存在，防止相关参数因目录不存在而报错
mkdir -p ./logs
mkdir -p ./tmp

# 无限循环，实现守护进程
while true; do
    echo
    echo "======================================================="
    echo "Starting Application..."
    echo "JAR Path: \"$JAR_PATH\""
    echo "Timestamp: $(date '+%Y-%m-%d %H:%M:%S')"
    echo "======================================================="
    echo

    # 运行 Java 程序，将所有配置好的JAVA_OPTS传递给它
    java $JAVA_OPTS -jar "$JAR_PATH"
    EXIT_CODE=$?

    echo
    echo "======================================================="
    echo "Application exited with code: $EXIT_CODE"
    echo "Timestamp: $(date '+%Y-%m-%d %H:%M:%S')"
    echo "======================================================="
    echo

    # 检查退出码是否为我们约定的“重启更新”码
    if [ $EXIT_CODE -eq $UPDATE_EXIT_CODE ]; then
        # ---- 更新流程 ----
        echo "[UPDATE] Update exit code detected. Starting update process..."
        sleep 2 # 等待，确保文件句柄释放

        if [ -f "$TEMP_JAR_PATH" ]; then
            echo "[UPDATE] Found update file: '$TEMP_JAR_PATH'."
            echo "[UPDATE] Attempting to replace '$JAR_PATH'..."

            # 使用 mv -f 命令来强制覆盖旧文件
            if mv -f "$TEMP_JAR_PATH" "$JAR_PATH"; then
                echo "[SUCCESS] Update complete! '$JAR_PATH' has been successfully updated."
            else
                echo "[ERROR] Update failed: Could not replace the JAR file. Check permissions."
                echo "[INFO] Restarting with the old version."
            fi
        else
            echo "[ERROR] Update failed: Temporary update file '$TEMP_JAR_PATH' not found!"
            echo "[INFO] Aborting update and restarting with the old version."
        fi
    else
        echo "[INFO] Normal exit or crash detected (Code: $EXIT_CODE). Restarting application..."
    fi

    echo "[INFO] Looping back to restart the application in 3 seconds..."
    sleep 3
done
