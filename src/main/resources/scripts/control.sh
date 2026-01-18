#!/bin/bash

# ===================== 配置区 =====================
WORK_DIR="."
APP_NAME="winefox-bot-shiro"
DAEMON_SCRIPT_NAME="start-daemon.sh"

# 日志与PID配置
LOG_DIR="./logs"
# 1. 修改：固定日志文件名，不再包含日期
LOG_FILE="${LOG_DIR}/${APP_NAME}.log"
PID_FILE="./${APP_NAME}.pid"
# ==================================================

# --- 初始化 ---
cd "$WORK_DIR" || exit 1
WORK_DIR=$(pwd)
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

# 启动
start() {
    if get_pid > /dev/null; then
        echo "${APP_NAME} 已经在运行 (PID: $(get_pid))."
        return 0
    fi

    echo "正在启动 ${APP_NAME} ..."

    # 2. 修改：使用追加模式 (>>) 写入固定的日志文件
    nohup setsid ./${DAEMON_SCRIPT_NAME} >> "${LOG_FILE}" 2>&1 &

    local new_pid=$!
    sleep 1

    if ps -p "$new_pid" > /dev/null 2>&1; then
        echo "$new_pid" > "$PID_FILE"
        echo "${APP_NAME} 启动成功 (PID: ${new_pid})."
        echo "日志输出至: ${LOG_FILE}"
    else
        echo "启动失败。"
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
        echo "日志文件: ${LOG_FILE}"
    else
        echo "${APP_NAME} 未运行."
    fi
}

# 查看日志
logs() {
    if [ ! -f "$LOG_FILE" ]; then
        echo "日志文件不存在: ${LOG_FILE}"
        return 1
    fi

    echo "正在实时追踪日志: ${LOG_FILE} (按 Ctrl+C 退出)"
    # 3. 修改：直接 tail -f 固定文件
    tail -f "$LOG_FILE"
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
