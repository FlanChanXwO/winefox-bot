package com.github.winefoxbot.core.util;

import cn.hutool.core.convert.NumberChineseFormatter;
import cn.hutool.core.util.NumberUtil;

public class NumberParserUtil {

    /**
     * 解析数量字符串
     * @param text 包含数字或中文数字的字符串
     * @return 返回解析后的数字，如果为空返回1，解析失败返回-1
     */
    public static int parseCount(String text) {
        if (text == null || text.isBlank()) {
            return 1;
        }

        // 尝试解析阿拉伯数字
        int n = NumberUtil.parseInt(text, -1);

        // 如果解析失败，尝试解析中文数字
        if (n == -1) {
            try {
                // 使用 Hutool 或类似的库
                n = NumberChineseFormatter.chineseToNumber(text);
            } catch (Exception e) {
                return -1;
            }
        }

        return n;
    }
}
