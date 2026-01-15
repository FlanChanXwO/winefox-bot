package com.github.winefoxbot.plugins.imgexploration.model.dto;

public record SearchResultItemDTO(
    String title,
    String url,
    String thumbnail,
    byte[] thumbnailBytes, // 可以是 mutable 的，但在 record 中我们需要在处理阶段替换列表或者使用额外容器
    String source,    // e.g., "SauceNAO", "Google"
    String similarity, 
    String description,
    String domain
) {
    // 为了方便后续填充图片字节，我们可以创建一个 helper 方法返回新实例
    public SearchResultItemDTO withThumbnailBytes(byte[] bytes) {
        return new SearchResultItemDTO(title, url, thumbnail, bytes, source, similarity, description, domain);
    }
}


