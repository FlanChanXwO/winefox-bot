#!/bin/bash

# ===================== 配置区 =====================
# 工作目录，脚本将在此目录下执行
# 一般保持默认"."即可，表示在当前目录
WORK_DIR="."

# 应用名称，用于日志、PID文件和进程识别
APP_NAME="winefox-bot-shiro"

# 实际执行应用循环和更新逻辑的脚本文件名
DAEMON_SCRIPT_NAME="./start-daemon.sh"

# --- 日志与PID配置 ---
# 日志文件存放目录
LOG_DIR="./logs"
# PID文件路径，用于记录主守护进程的ID
PID_FILE="./${APP_NAME}.pid"
# ==================================================

# --- 脚本自检 ---
# 确保工作目录存在
cd "$WORK_DIR" || { echo "错误: 无法进入工作目录 ${WORK_DIR}"; exit 1; }
# 确保守护脚本存在且可执行
if [ ! -x "$DAEMON_SCRIPT_NAME" ]; then
    echo "错误: 守护脚本 '${DAEMON_SCRIPT_NAME}' 不存在或没有执行权限。"
    echo "请确保该文件存在，并执行 'chmod +x ${DAEMON_SCRIPT_NAME}'"
    exit 1
fi

# 确保日志目录存在
mkdir -p "$LOG_DIR"


# 获取当前主守护进程的PID
get_pid() {
    if [ -f "$PID_FILE" ]; then
        PID=$(cat "$PID_FILE")
        # 检查PID对应的进程是否存在
        if ps -p "$PID" > /dev/null 2>&1; then
            echo "$PID"
            return 0
        else
            # 进程不存在，说明PID文件已失效，清理它
            echo "警告: 发现无效的PID文件，已自动清理。"
            rm -f "$PID_FILE"
            return 1
        fi
    fi
    return 1
}

# 启动应用
start() {
    local pid
    pid=$(get_pid)

    if [ -n "$pid" ]; then
        echo "${APP_NAME} 已经在后台运行 (主进程 PID: ${pid})."
        return 0
    fi

    echo "正在后台启动 ${APP_NAME}..."

    # 日志文件按天归档
    local logfile="${LOG_DIR}/$(date +%Y-%m-%d).log"

    # 使用 nohup 在后台启动 DAEMON_SCRIPT_NAME
    # DAEMON_SCRIPT_NAME 内部的循环将保证Java进程的持续运行和更新
    # 所有来自 DAEMON_SCRIPT_NAME 和 Java 进程的输出都将被重定向到日志文件
    nohup ./${DAEMON_SCRIPT_NAME} >> "${logfile}" 2>&1 &

    # 获取刚启动的后台脚本的PID (这就是我们的主守护进程)
    local new_pid=$!
    sleep 1 # 等待片刻

    # 确认进程是否成功启动
    if ps -p "$new_pid" > /dev/null 2>&1; then
        echo "$new_pid" > "$PID_FILE"
        echo "${APP_NAME} 启动成功 (主进程 PID: ${new_pid})."
        echo "所有日志将写入: ${logfile}"
    else
        echo "启动失败: 未能找到进程ID ${new_pid}."
        echo "请检查日志文件以获取详细错误信息: ${logfile}"
        # 尝试显示最近的日志帮助排查问题
        tail -n 20 "${logfile}"
        return 1
    fi
}

# 停止应用
stop() {
    local pid
    pid=$(get_pid)

    if [ -z "$pid" ]; then
        echo "${APP_NAME} 未在运行."
        return 0
    fi

    echo "正在停止 ${APP_NAME} (主进程 PID: ${pid})..."

    # 停止主守护进程(DAEMON_SCRIPT_NAME)
    # 这会导致其内部循环终止，从而使Java进程不会被再次拉起
    kill -15 "$pid"

    # 等待主守护进程退出
    for i in {1..5}; do
        if ! ps -p "$pid" > /dev/null 2>&1; then
            break
        fi
        echo -n "."
        sleep 1
    done

    # 主守护进程退出后，Java进程可能仍在运行，需要单独停止
    # 通过 pgrep -P $pid 找到由主守护进程启动的所有子进程（即Java进程）
    local child_pids=$(pgrep -P "$pid")
    if [ -n "$child_pids" ]; then
        echo "正在停止Java子进程: ${child_pids}..."
        kill -15 $child_pids
        sleep 2
        # 强制杀死仍然存在的子进程
        kill -9 $child_pids 2>/dev/null
    fi

    # 确认并清理
    if ps -p "$pid" > /dev/null 2>&1; then
        echo "警告: 主守护进程未能优雅停止，正在强制杀死..."
        kill -9 "$pid"
    fi

    rm -f "$PID_FILE"
    echo "${APP_NAME} 已成功停止."
}

# 查看状态
status() {
    local pid
    pid=$(get_pid)

    if [ -n "$pid" ]; then
        echo "${APP_NAME} 主守护进程正在运行 (PID: ${pid})."
        # 尝试查找由它启动的Java进程
        local java_pid=$(pgrep -P "$pid" java)
        if [ -n "$java_pid" ]; then
            echo " -> Java 应用进程正在运行 (PID: ${java_pid})."
        else
            echo " -> 警告: 未找到关联的Java进程，可能正在重启或已崩溃。"
        fi
        echo "今日日志: ${LOG_DIR}/$(date +%Y-%m-%d).log"
    else
        echo "${APP_NAME} 未运行."
    fi
}

# 重启应用
restart() {
    echo "正在重启 ${APP_NAME}..."
    stop
    sleep 2 # 在启动前稍作等待
    start
}

# 查看日志
logs() {
    local logfile="${LOG_DIR}/$(date +%Y-%m-%d).log"

    if [ ! -f "$logfile" ]; then
        echo "今日日志文件不存在: ${logfile}"
        local latest_log=$(ls -t ${LOG_DIR}/*.log 2>/dev/null | head -n 1)
        if [ -n "$latest_log" ]; then
            echo "是否要查看最近的日志文件: ${latest_log} ? (y/n)"
            read -r answer
            if [[ "$answer" =~ ^[Yy]$ ]]; then
                logfile="$latest_log"
            else
                return 1
            fi
        else
            return 1
        fi
    fi

    # 使用 -f 参数实时跟踪日志
    if [ "$2" == "-f" ]; then
        echo "正在实时跟踪日志 (按 Ctrl+C 退出)..."
        tail -f -n 100 "$logfile"
    else
        # 默认使用 less 分页查看，更友好
        less +G "$logfile" # +G 直接跳转到文件末尾
    fi
}

# --- 主逻辑 ---
case "$1" in
    start)   start ;;
    stop)    stop ;;
    restart) restart ;;
    status)  status ;;
    logs)    logs "$@" ;;
    *)
        echo "一个健壮的应用控制脚本"
        echo "用法: $0 {start|stop|restart|status|logs [-f]}"
        exit 1
        ;;
esac
