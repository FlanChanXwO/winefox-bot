package com.github.winefoxbot.core.model.vo.webui.req;

// 保存文件内容请求
public record SaveFileRequest(
    String path,
    String content
) {}