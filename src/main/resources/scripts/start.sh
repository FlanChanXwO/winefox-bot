#!/bin/bash

# --- 配置 ---
JAR_FILE="winefox-bot.jar"
RESTART_CODE=5
LOGS_DIR="logs"

# 获取脚本所在的绝对路径
# 这确保了无论你在哪个目录下执行 ./start.sh，它都能找到正确的 JAR 文件和 logs 目录
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" &>/dev/null && pwd)

# --- 创建日志目录 (如果不存在) ---
if [ ! -d "$SCRIPT_DIR/$LOGS_DIR" ]; then
  echo "Creating log directory: $LOGS_DIR"
  mkdir -p "$SCRIPT_DIR/$LOGS_DIR"
fi

# 启动无限循环，脚本本身只有在退出码不是重启码时才会真正退出
while true; do
  # --- 生成带时间戳的日志文件名 ---
  LOG_FILE="$SCRIPT_DIR/$LOGS_DIR/$(date +'%Y-%m-%d_%H-%M-%S').log"

  # --- 将所有输出重定向到日志文件 ---
  # exec > >(tee -a "$LOG_FILE") 2>&1
  # 上面这行 tee 的方法很好，但为了简单和最大兼容性，我们直接重定向

  {
    echo ""
    echo "======================================================"
    echo "          SCRIPT EXECUTION STARTED AT $(date)"
    echo "======================================================"
    echo ""
    echo "Starting $JAR_FILE..."
    echo "Log file for this session is: $LOG_FILE"
    echo ""

    # 执行 java 命令
    java -jar "$SCRIPT_DIR/$JAR_FILE"

    # 捕获退出码。$? 是 shell 中的一个特殊变量，保存了上一个命令的退出码
    EXIT_CODE=$?

    echo ""
    echo "==================== DEBUG INFO ===================="
    echo ""
    echo "  JAVA process exited at: $(date)"
    echo "  The captured EXIT_CODE is: [$EXIT_CODE]"
    echo "  The expected RESTART_CODE is: [$RESTART_CODE]"
    echo ""
    echo "===================================================="
    echo ""

    # 检查退出码是否为重启码
    if [ "$EXIT_CODE" -eq "$RESTART_CODE" ]; then
      echo "[SUCCESS] Exit code matches. Restarting in 3 seconds..."
      sleep 3
      echo "Restarting now..."
      # 循环将自动继续，所以这里不需要 'continue' 或 'goto'
    else
      echo "[FAILURE] Exit code does not match. Will not restart."
      echo "Script will now exit."
      break # 退出 while 循环
    fi
  } >> "$LOG_FILE" 2>&1 # 将代码块的所有标准输出和标准错误输出都重定向到日志文件

  # 如果脚本因为非重启原因退出，在控制台也显示一下最终信息
  if [ "$EXIT_CODE" -ne "$RESTART_CODE" ]; then
    echo ""
    echo "================================================================="
    echo ""
    echo "  The bot process has finished."
    echo "  - Final exit code was: $EXIT_CODE"
    echo "  - Detailed log has been saved to:"
    echo "    $LOG_FILE"
    echo ""
    echo "================================================================="
    # 退出循环后，脚本自然结束
  fi

done