#!/bin/bash

# ===================== 配置区 =====================
WORK_DIR="."
LOG_DIR="./logs"
PID_FILE="./winefox-bot.pid"
JAR_FILE="./winefox-bot-1.0.0.jar"
JAVA_CMD=(java -jar "$JAR_FILE")   # 用数组更安全
# ==================================================

mkdir -p "$LOG_DIR"

# 获取当天日志文件路径（每次调用都实时计算）
get_log_file() {
    local today
    today=$(date +"%Y-%m-%d")
    echo "$LOG_DIR/$today.log"
}

# 检查是否运行中
is_running() {
    if [ -f "$PID_FILE" ]; then
        PID=$(cat "$PID_FILE")
        if ps -p "$PID" > /dev/null 2>&1; then
            return 0
        else
            rm -f "$PID_FILE"
        fi
    fi
    return 1
}

# 日志切割器：按天写入不同文件
# - 从 stdin 读日志
# - 每次日期变化就切换输出文件
log_rotator() {
    local current_day=""
    local logfile=""

    while IFS= read -r line; do
        local now_day
        now_day=$(date +"%Y-%m-%d")

        if [ "$now_day" != "$current_day" ]; then
            current_day="$now_day"
            logfile="$LOG_DIR/$current_day.log"
            mkdir -p "$LOG_DIR"
        fi

        printf '%s\n' "$line" >> "$logfile"
    done
}

start() {
    if is_running; then
        echo "winefox-bot-shiro 已在运行 (PID $(cat "$PID_FILE"))"
        return 0
    fi

    mkdir -p "$LOG_DIR"

    echo "启动 winefox-bot-shiro..."
    cd "$WORK_DIR" || exit 1

    nohup bash -c '
        env ALL_PROXY= all_proxy= java -jar '"$JAR_FILE"' 2>&1 |
        while IFS= read -r line; do
            logfile="'"$LOG_DIR"'/$(date +%Y-%m-%d).log"
            echo "$line" >> "$logfile"
        done
    ' >/dev/null 2>&1 &

    sleep 0.5

    JAVA_PID=$(pgrep -f "java .*${JAR_FILE}" | head -n 1)
    if [ -z "$JAVA_PID" ]; then
        echo "启动失败：未找到 Java 进程"
        exit 1
    fi

    echo "$JAVA_PID" > "$PID_FILE"
    echo "启动成功 (PID $JAVA_PID)"
    echo "今日日志：$LOG_DIR/$(date +%Y-%m-%d).log"
}




stop() {
    if is_running; then
        PID=$(cat "$PID_FILE")
        echo "停止 winefox-bot-shiro (PID $PID)..."

        # 先尝试优雅结束 Java
        kill "$PID" 2>/dev/null
        sleep 1

        if ps -p "$PID" > /dev/null 2>&1; then
            echo "进程未退出，强制杀掉..."
            kill -9 "$PID" 2>/dev/null
        fi

        # 再清理同一条管道链上的 bash/setsid/rotator（按进程组/父子关系更稳）
        # 尝试杀掉包含该 jar 的相关 bash 管道进程
        pkill -f "java .*${JAR_FILE}" 2>/dev/null
        pkill -f "log_rotator" 2>/dev/null

        rm -f "$PID_FILE"
        echo "已停止."
    else
        echo "winefox-bot-shiro 未运行."
    fi
}

status() {
    if is_running; then
        echo "运行中 (PID $(cat "$PID_FILE")). 今日日志：$(get_log_file)"
    else
        echo "未运行."
    fi
}

restart() {
    echo "重启 winefox-bot-shiro..."
    stop
    sleep 1
    start
}

logs() {
    local file
    file=$(get_log_file)

    if [ ! -f "$file" ]; then
        echo "今天还没有日志文件：$file"
        return 1
    fi

    if [ "$2" == "-f" ]; then
        tail -f "$file"
    else
        cat "$file"
    fi
}

case "$1" in
    start) start ;;
    stop) stop ;;
    restart) restart ;;
    status) status ;;
    logs) logs "$@" ;;
    *)
        echo "用法: $0 {start|stop|restart|status|logs [-f]}"
        exit 1
        ;;
esac
