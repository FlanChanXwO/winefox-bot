package com.github.winefoxbot.plugins.fortune.model.vo;

/**
 * 运势渲染视图数据
 */
public record FortuneRenderVO(
    String username,        // 用户昵称
    String dateStr,         // 日期字符串 (yyyy-MM-dd)
    String title,           // 运势标题 (大吉/中吉...)
    String description,     // 运势描述
    String extraMessage,    // 额外描述 (宜/忌等)
    int starCount,          // 星星数量
    String imageUrl,        // 二次元图片链接
    String themeColor       // 主题色 (可选，用于前端CSS动态变色)
) {
}
