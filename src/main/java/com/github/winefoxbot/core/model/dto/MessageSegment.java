package com.github.winefoxbot.core.model.dto;

import java.util.LinkedHashMap;

// 一个通用的消息段模型
public class MessageSegment<T> {
    public String type;
    public T data;

    public MessageSegment(String type, T data) {
        this.type = type;
        this.data = data;
    }


    // 用于文本段的 "data" 部分的模型
    public static class TextData {
        public String text;

        public TextData(String text) {
            this.text = text;
        }
    }

    // 用于CQ码的 "data" 部分的模型，使用Map来保持灵活性
    public static class CQCodeData extends LinkedHashMap<String, String> {}
}

