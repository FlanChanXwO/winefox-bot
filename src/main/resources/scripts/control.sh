#!/bin/bash

# ===================== 配置区 =====================
WORK_DIR="."
APP_NAME="winefox-bot-shiro"
DAEMON_SCRIPT_NAME="start-daemon.sh"

# 日志目录 (脚本本身不再写入，仅供 logs 命令读取 Java 生成的日志)
LOG_DIR="./logs"
PID_FILE="./${APP_NAME}.pid"
# ==================================================

# --- 初始化 ---
cd "$WORK_DIR" || exit 1
WORK_DIR=$(pwd)

# 确保日志目录存在 (给 Java 应用用)
mkdir -p "$LOG_DIR"

# 获取PID
get_pid() {
    [ ! -f "$PID_FILE" ] && return 1
    local pid
    pid=$(cat "$PID_FILE")
    [ -z "$pid" ] && return 1
    if ps -p "$pid" > /dev/null 2>&1; then
        echo "$pid"
        return 0
    else
        rm -f "$PID_FILE"
        return 1
    fi
}

# 启动 (不再生成 Shell 日志文件，输出丢弃至 /dev/null)
start() {
    if get_pid > /dev/null; then
        echo "${APP_NAME} 已经在运行 (PID: $(get_pid))."
        return 0
    fi

    echo "正在启动 ${APP_NAME} ..."

    # > /dev/null 2>&1 : 不再生成 nohup.out 或 日期日志文件
    nohup setsid ./${DAEMON_SCRIPT_NAME} > /dev/null 2>&1 &

    local new_pid=$!
    sleep 1

    if ps -p "$new_pid" > /dev/null 2>&1; then
        echo "$new_pid" > "$PID_FILE"
        echo "${APP_NAME} 启动成功 (PID: ${new_pid})."
    else
        echo "启动失败，进程未驻留。"
        return 1
    fi
}

# 停止
stop() {
    local pid
    pid=$(get_pid)
    [ -z "$pid" ] && echo "${APP_NAME} 未运行." && return 0

    echo "正在停止 ${APP_NAME} (PID: ${pid})..."
    kill -- -"$pid" # 杀掉进程组

    local countdown=10
    while ((countdown > 0)); do
        if ! get_pid > /dev/null; then
            echo "已停止."
            rm -f "$PID_FILE"
            return 0
        fi
        sleep 1
        ((countdown--))
    done

    kill -9 -- -"$pid" 2>/dev/null
    rm -f "$PID_FILE"
    echo "已强制停止."
}

# 状态
status() {
    local pid
    pid=$(get_pid)
    if [ -n "$pid" ]; then
        echo "${APP_NAME} 正在运行 (PID: ${pid})."
        echo "Java 进程: $(pgrep -g "$pid" java | tr '\n' ' ')"
    else
        echo "${APP_NAME} 未运行."
    fi
}

# 查看日志 (简化：直接实时追踪最新的日志文件)
logs() {
    # 查找 logs 目录下修改时间最新的 .log 文件
    local latest_log
    latest_log=$(ls -t "${LOG_DIR}"/*.log 2>/dev/null | head -n 1)

    if [ -z "$latest_log" ]; then
        echo "未找到日志文件 (目录 ${LOG_DIR} 为空)."
        return 1
    fi

    echo "正在实时追踪最新日志: ${latest_log}"
    tail -f "$latest_log"
}

# 重启
restart() {
    stop
    sleep 1
    start
}

# 入口
case "$1" in
    start)   start ;;
    stop)    stop ;;
    restart) restart ;;
    status)  status ;;
    logs)    logs ;;
    *)       echo "用法: $0 {start|stop|restart|status|logs}" ;;
esac
