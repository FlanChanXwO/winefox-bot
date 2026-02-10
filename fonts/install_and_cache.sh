#!/bin/bash

#   脚本名称: install_fonts.sh
#   功    能: 在 Linux 系统中一键安装字体并刷新缓存

# --- 配置 ---
# 设置自定义字体安装的目标目录
# 推荐放在 /usr/share/fonts/ 下，使其对所有用户可用
DEST_DIR="/usr/share/fonts/custom-font"


# --- 颜色定义 (可选, 使输出更美观) ---
GREEN="\033[0;32m"
YELLOW="\033[1;33m"
RED="\033[0;31m"
NC="\033[0m" # No Color


# --- 主逻辑 ---

# 步骤 1: 检查是否以 root 权限运行
if [ "$(id -u)" -ne 0 ]; then
    echo -e "${YELLOW}警告: 此脚本需要 root 权限来安装字体到系统目录。${NC}"
    echo "正在尝试使用 sudo 重新运行..."
    # 使用 sudo 并将所有参数传递给新实例
    sudo "$0" "$@"
    # 检查 sudo 的退出状态
    if [ $? -ne 0 ]; then
        echo -e "${RED}错误: sudo 执行失败。请确保您有 sudo 权限或以 root 用户身份运行。${NC}"
        exit 1
    fi
    # 如果 sudo 成功, 当前脚本实例退出
    exit 0
fi

echo -e "${GREEN}权限检查通过，以 root 权限继续执行...${NC}"

# 步骤 2: 确定字体源目录
SOURCE_DIR=""
if [ -n "$1" ]; then
    # 如果提供了第一个参数, 将其作为源目录
    SOURCE_DIR="$1"
    if [ ! -d "$SOURCE_DIR" ]; then
        echo -e "${RED}错误: 指定的目录 '$SOURCE_DIR' 不存在。${NC}"
        exit 1
    fi
    echo "将从指定目录 '$SOURCE_DIR' 安装字体。"
else
    # 否则, 使用脚本所在的目录
    SOURCE_DIR="$(pwd)"
    echo "将在当前目录 '$SOURCE_DIR' 中查找字体文件。"
fi

# 步骤 3: 检查是否存在字体文件
# 使用 find 和 wc -l 来统计字体文件数量
font_count=$(find "$SOURCE_DIR" -maxdepth 1 -type f \( -iname "*.ttf" -o -iname "*.otf" -o -iname "*.ttc" \) | wc -l)

if [ "$font_count" -eq 0 ]; then
    echo -e "${YELLOW}在 '$SOURCE_DIR' 中未找到任何字体文件 (.ttf, .otf, .ttc)。${NC}"
    echo "脚本执行完毕，未做任何更改。"
    exit 0
fi

echo -e "发现 ${GREEN}$font_count${NC} 个字体文件。准备开始安装..."

# 步骤 4: 创建目标目录 (如果不存在)
if [ ! -d "$DEST_DIR" ]; then
    echo "创建系统字体目录: $DEST_DIR"
    mkdir -p "$DEST_DIR"
    if [ $? -ne 0 ]; then
        echo -e "${RED}错误: 创建目录 '$DEST_DIR' 失败。${NC}"
        exit 1
    fi
fi

# 步骤 5: 复制字体文件
echo "正在复制字体文件到 $DEST_DIR ..."
# 使用 find 来处理多种后缀和大小写不敏感的情况
find "$SOURCE_DIR" -maxdepth 1 -type f \( -iname "*.ttf" -o -iname "*.otf" -o -iname "*.ttc" \) -exec cp -v {} "$DEST_DIR/" \;

if [ $? -ne 0 ]; then
    echo -e "${RED}错误: 复制字体文件时发生错误。${NC}"
    exit 1
fi

# 步骤 6: 设置正确的权限
echo "正在为新字体设置权限..."
chmod 644 "$DEST_DIR"/*
# 确保目录权限正确
chmod 755 "$DEST_DIR"

# 步骤 7: 刷新字体缓存
echo "正在更新系统字体缓存，请稍候..."
fc-cache -f -v

if [ $? -eq 0 ]; then
    echo -e "${GREEN}=======================================================${NC}"
    echo -e "${GREEN}          字体安装并刷新缓存成功!                ${NC}"
    echo -e "${GREEN}=======================================================${NC}"
    echo "你现在可以在应用程序（如 LibreOffice, GIMP）的字体选择器中找到新安装的字体了。"
else
    echo -e "${RED}错误: 刷新字体缓存失败 (fc-cache 命令执行出错)。${NC}"
    echo "请检查 fontconfig 是否已正确安装 (sudo apt-get install fontconfig)。"
fi

exit 0
