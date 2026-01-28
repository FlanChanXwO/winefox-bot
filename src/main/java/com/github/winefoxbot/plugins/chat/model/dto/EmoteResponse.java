package com.github.winefoxbot.plugins.chat.model.dto;

// 定义返回给 AI 的结构
public record EmoteResponse(int id, String path, String description) {
}