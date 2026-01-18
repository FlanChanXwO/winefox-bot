package com.github.winefoxbot.core.model.vo.webui.resp;

// 文件列表项响应
public record FileItemResponse(
    String id,          // 使用路径的Hash作为ID
    String name,        // 文件名
    String path,        // 完整绝对路径
    String date,        // 修改时间字符串
    String size,        // 格式化后的大小 (如 1.2 MB)
    String type,        // "file" 或 "folder"
    long rawSize,        // 原始字节大小，用于排序
    boolean editable // 是否可编辑
) {}





