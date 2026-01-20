package com.github.winefoxbot.core.model.vo.webui.req.filemanager;

// 重命名请求
public record RenameRequest(
    String oldPath,
    String newName
) {}
