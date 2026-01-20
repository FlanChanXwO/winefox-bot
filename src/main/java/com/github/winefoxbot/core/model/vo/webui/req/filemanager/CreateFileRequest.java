package com.github.winefoxbot.core.model.vo.webui.req.filemanager;

// 创建文件/文件夹请求
public record CreateFileRequest(
    String path,      // 当前目录路径
    String name,      // 新名称
    boolean isFolder  // 是否是文件夹
) {}