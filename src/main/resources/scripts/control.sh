#!/bin/bash

# ===================== 配置区 =====================
# 工作目录，脚本将在此目录下执行
# 一般保持默认"."即可，表示在当前目录
WORK_DIR="."

# 应用名称，用于日志、PID文件和进程识别
APP_NAME="winefox-bot-shiro"

# 实际执行应用循环和更新逻辑的脚本文件名
DAEMON_SCRIPT_NAME="start-daemon.sh"

# --- 日志与PID配置 ---
# 日志文件存放目录
LOG_DIR="./logs"
# PID文件路径，用于记录主守护进程的ID
PID_FILE="./${APP_NAME}.pid"
# ==================================================

# --- 环境检查与初始化 ---
# 切换到工作目录，如果失败则退出
cd "$WORK_DIR" || { echo "错误: 无法进入工作目录 ${WORK_DIR}"; exit 1; }
WORK_DIR=$(pwd) # 获取绝对路径，避免后续操作因路径问题出错

# 确保守护脚本存在且可执行
if [ ! -x "$DAEMON_SCRIPT_NAME" ]; then
    echo "错误: 守护脚本 '${DAEMON_SCRIPT_NAME}' 不存在或没有执行权限。" >&2
    echo "请确保该文件存在于 ${WORK_DIR}，并已执行 'chmod +x ${DAEMON_SCRIPT_NAME}'" >&2
    exit 1
fi

# 确保日志目录存在
mkdir -p "$LOG_DIR"


# 获取当前主守护进程的PID
# 返回PID并返回0表示成功，返回1表示失败
get_pid() {
    if [ ! -f "$PID_FILE" ]; then
        return 1
    fi

    local pid
    pid=$(cat "$PID_FILE")
    if [ -z "$pid" ]; then
        return 1
    fi

    # 检查PID对应的进程是否存在
    if ps -p "$pid" > /dev/null 2>&1; then
        echo "$pid"
        return 0
    else
        # 进程不存在，说明PID文件已失效，清理它
        echo "警告: 发现无效的PID文件 (PID: ${pid})，已自动清理。" >&2
        rm -f "$PID_FILE"
        return 1
    fi
}

# 启动应用
start() {
    if get_pid > /dev/null; then
        echo "${APP_NAME} 已经在后台运行 (主进程 PID: $(get_pid))."
        return 0
    fi

    echo "正在后台启动 ${APP_NAME}..."

    # 日志文件按天归档
    local logfile="${LOG_DIR}/$(date +%Y-%m-%d).log"

    # 使用 nohup 在后台启动 DAEMON_SCRIPT_NAME
    # 将其放入一个新的进程组 (setsid)，便于后续管理整个进程树
    nohup setsid ./${DAEMON_SCRIPT_NAME} >> "${logfile}" 2>&1 &

    local new_pid=$!
    sleep 1

    if ps -p "$new_pid" > /dev/null 2>&1; then
        echo "$new_pid" > "$PID_FILE"
        echo "${APP_NAME} 启动成功 (主守护进程 PID: ${new_pid})."
        echo "所有日志将写入: ${logfile}"
    else
        echo "错误: 启动失败。未能确认进程 (PID: ${new_pid}) 是否在运行。" >&2
        echo "请检查日志文件以获取详细错误信息: ${logfile}" >&2
        if [ -f "$logfile" ]; then
            echo "--- 最近的日志内容 ---" >&2
            tail -n 20 "${logfile}" >&2
        fi
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

    # **[改进]** 通过进程组 (Process Group ID) 来杀死所有相关进程
    # setsid 启动时，pid 就是 pgid
    # -${pid} 表示杀死整个进程组
    kill -- -"$pid"

    local countdown=10
    while ((countdown > 0)); do
        if ! get_pid > /dev/null; then
            echo "${APP_NAME} 已成功停止."
            rm -f "$PID_FILE"
            return 0
        fi
        echo -n "."
        sleep 1
        ((countdown--))
    done

    # 如果10秒后仍在运行，则强制杀死
    echo "警告: 优雅停止超时，正在强制终止..." >&2
    kill -9 -- -"$pid" 2>/dev/null
    rm -f "$PID_FILE"
    echo "${APP_NAME} 已被强制停止."
}


# 查看状态
status() {
    local pid
    pid=$(get_pid)

    if [ -z "$pid" ]; then
        echo "${APP_NAME} 未运行."
        return 0
    fi

    echo "${APP_NAME} 主守护进程正在运行 (PID: ${pid})."
    # **[改进]** 通过 pgrep -g <pgid> 查找进程组内的java进程
    local java_pid
    java_pid=$(pgrep -g "$pid" java)
    if [ -n "$java_pid" ]; then
        # pgrep可能返回多个PID
        echo " -> Java 应用进程正在运行 (PID(s): ${java_pid//$'\n'/' '})."
    else
        echo " -> 警告: 未找到关联的Java进程，可能正在重启或已崩溃。"
    fi
    echo "今日日志: ${LOG_DIR}/$(date +%Y-%m-%d).log"
}


# 重启应用
restart() {
    echo "正在重启 ${APP_NAME}..."
    stop
    sleep 2
    start
}

# 查看日志
logs() {
    local logfile="${LOG_DIR}/$(date +%Y-%m-%d).log"

    if [ ! -f "$logfile" ]; then
        echo "今日日志文件不存在: ${logfile}"
        # **[改进]** 非交互式地查找并提示最新的日志文件
        local latest_log
        latest_log=$(ls -t "${LOG_DIR}"/*.log 2>/dev/null | head -n 1)
        if [ -n "$latest_log" ]; then
            echo "提示: 最新的日志文件是: ${latest_log}"
            echo "你可以使用 'less ${latest_log}' 或 'tail -f ${latest_log}' 查看。"
        fi
        return 1
    fi

    # 使用 -f 参数实时跟踪日志
    if [ "$2" == "-f" ]; then
        echo "正在实时跟踪日志 (按 Ctrl+C 退出)..."
        tail -n 100 -f "$logfile"
    else
        # **[改进]** 使用更友好的 less 参数
        less -R -S +G "$logfile"
    fi
}

# 显示用法
usage() {
    echo ""
    echo "========================================================"
    echo "  WineFox Bot Control Script (Linux/macOS)"
    echo "========================================================"
    echo ""
    echo "用法: $0 [command]"
    echo ""
    echo "命令列表:"
    echo "  start    - 启动机器人 (后台运行)"
    echo "  stop     - 停止机器人"
    echo "  restart  - 重启机器人"
    echo "  status   - 查看运行状态"
    echo "  logs     - 查看今日日志 (分页显示)"
    echo "  logs -f  - 实时跟踪日志输出"
    echo ""
}

# --- 主逻辑 ---
# 如果没有参数，显示用法
if [ $# -eq 0 ]; then
    usage
    exit 1
fi

case "$1" in
    start)   start ;;
    stop)    stop ;;
    restart) restart ;;
    status)  status ;;
    logs)    logs "$@" ;;
    *)       usage ;;
esac