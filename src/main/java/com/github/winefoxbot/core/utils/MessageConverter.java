package com.github.winefoxbot.core.utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OneBot v11 消息格式转换工具类
 * <p>
 * 基于 Hutool 重构，移除 Gson 依赖，支持多模态解析
 *
 * @author FlanChan (badapple495@outlook.com)
 * @since 2026-01-07-5:27
 */
@Slf4j
public final class MessageConverter {

    private MessageConverter() {
        throw new UnsupportedOperationException("Utility class");
    }

    private static final Pattern cqCodePattern = Pattern.compile("\\[CQ:([a-zA-Z0-9_.-]+)((,.*?)*?)\\]");

    /**
     * 将包含 CQ 码的字符串解析为 JSON 数组字符串。
     *
     * @param message 输入的消息字符串 (CQ码格式)
     * @return JSON 数组字符串
     */
    public static String parseCQtoJSONStr(String message) {
        return parseCQToJSONArray(message).toString();
    }

    /**
     * 核心解析方法：将包含 CQ 码的字符串解析为 Hutool JSONArray。
     * 结果示例：[{"type":"text","data":{"text":"Hello"}}, {"type":"image","data":{"file":"..."}}]
     *
     * @param message 输入的消息字符串
     * @return 包含消息段的 JSONArray
     */
    public static JSONArray parseCQToJSONArray(String message) {
        JSONArray result = JSONUtil.createArray();

        // 如果输入是 null 或空字符串，直接返回空数组
        if (StrUtil.isEmpty(message)) {
            return result;
        }

        Matcher matcher = cqCodePattern.matcher(message);
        int lastEnd = 0;

        while (matcher.find()) {
            // 1. 添加在 CQ 码之前的纯文本段
            if (matcher.start() > lastEnd) {
                String text = message.substring(lastEnd, matcher.start());
                result.add(createSegment("text", JSONUtil.createObj().set("text", decodeCQText(text))));
            }

            // 2. 处理并添加 CQ 码段
            String cqType = matcher.group(1);
            String paramsStr = matcher.group(2);

            JSONObject cqData = JSONUtil.createObj();
            if (StrUtil.isNotEmpty(paramsStr)) {
                // 移除前导逗号并分割
                String[] params = paramsStr.substring(1).split(",");
                for (String param : params) {
                    int idx = param.indexOf('=');
                    if (idx > 0) {
                        String key = param.substring(0, idx);
                        String value = param.substring(idx + 1);
                        cqData.set(key, decodeCQValue(value));
                    }
                }
            }
            result.add(createSegment(cqType, cqData));

            lastEnd = matcher.end();
        }

        // 3. 添加最后一个 CQ 码之后的任何尾随文本
        if (lastEnd < message.length()) {
            String text = message.substring(lastEnd);
            result.add(createSegment("text", JSONUtil.createObj().set("text", decodeCQText(text))));
        }

        return result;
    }

    /**
     * 根据 CQ 字符串直接提取纯文本信息（移除图片、转换At等）。
     *
     * @param message CQ 码格式的原始字符串
     * @return 过滤后的纯文本
     */
    public static String getPlainTextMessage(String message) {
        if (StrUtil.isEmpty(message)) {
            return "";
        }
        // 复用解析逻辑，先转为结构化数据，确保处理逻辑（如转义、At处理）与 JSON 模式完全一致
        JSONArray array = parseCQToJSONArray(message);
        return getPlainTextMessage(array);
    }

    /**
     * 过滤 JSON 格式的消息字符串，提取纯文本信息。
     * 兼容处理 JSON 数组字符串和 JSON 对象字符串。
     *
     * @param jsonMessageStr 消息的 JSON 字符串表示
     * @return 过滤后的文本字符串
     */
    public static String getFilteredTextMessage(String jsonMessageStr) {
        if (StrUtil.isBlank(jsonMessageStr)) {
            return "";
        }

        try {
            if (JSONUtil.isTypeJSONArray(jsonMessageStr)) {
                return getPlainTextMessage(JSONUtil.parseArray(jsonMessageStr));
            } else if (JSONUtil.isTypeJSONObject(jsonMessageStr)) {
                return processSingleSegment(JSONUtil.parseObj(jsonMessageStr));
            } else {
                return jsonMessageStr;
            }
        } catch (Exception e) {
            log.error("JSON 解析失败，返回原始消息。Input: {}", jsonMessageStr, e);
            return jsonMessageStr;
        }
    }

    /**
     * 从消息段数组 JSONArray 中提取所有纯文本信息。
     *
     * @param jsonArray 代表消息段数组的 JSONArray
     * @return 拼接并过滤后的文本字符串
     */
    public static String getPlainTextMessage(JSONArray jsonArray) {
        if (jsonArray == null || jsonArray.isEmpty()) {
            return "";
        }
        StringBuilder plainText = new StringBuilder();
        for (Object item : jsonArray) {
            if (item instanceof JSONObject) {
                plainText.append(processSingleSegment((JSONObject) item));
            }
        }
        return plainText.toString().trim();
    }

    /**
     * 从消息中提取所有图片的 URL
     * @param message 原始消息字符串 (JSON 或 CQ码)
     * @return 图片 URL 列表
     */
    public static List<String> getImageUrls(String message) {
        if (StrUtil.isEmpty(message)) {
            return Collections.emptyList();
        }
        JSONArray array;
        try {
            // 尝试解析 JSON
            if (JSONUtil.isTypeJSONArray(message)) {
                array = JSONUtil.parseArray(message);
            } else {
                // 尝试解析 CQ 码
                array = parseCQToJSONArray(message);
            }
        } catch (Exception e) {
            log.warn("解析消息图片失败: {}", message, e);
            return Collections.emptyList();
        }

        List<String> imageUrls = new ArrayList<>();
        for (Object item : array) {
            if (item instanceof JSONObject segment) {
                String type = segment.getStr("type");
                if ("image".equals(type)) {
                    JSONObject data = segment.getJSONObject("data");
                    if (data != null) {
                        // 优先获取 url，如果没有则尝试获取 file (部分实现 file 也是 url)
                        String url = data.getStr("url");
                        if (StrUtil.isBlank(url)) {
                            url = data.getStr("file");
                        }
                        if (StrUtil.isNotBlank(url) && url.startsWith("http")) {
                            imageUrls.add(url);
                        }
                    }
                }
            }
        }
        return imageUrls;
    }


    /**
     * 【私有辅助方法】处理单个消息段 JSONObject，并返回其文本表示。
     *
     * @param segment 代表单个消息段的 JSONObject
     * @return 过滤后的文本字符串
     */
    private static String processSingleSegment(JSONObject segment) {
        if (segment == null) {
            return "";
        }

        String type = segment.getStr("type");
        JSONObject data = segment.getJSONObject("data");

        if (data == null) {
            // 部分非标准实现可能没有 data 字段，防御性处理
            return "";
        }

        if (type == null) {
            return "";
        }

        if (type.equals("text")) {
            return data.getStr("text", "");
        }

        return "";
    }

    private static JSONObject createSegment(String type, JSONObject data) {
        return JSONUtil.createObj().set("type", type).set("data", data);
    }

    /**
     * 解码 CQ 码参数值中的转义字符
     */
    private static String decodeCQValue(String value) {
        if (value == null) return "";
        return value.replace("&amp;", "&")
                .replace("&#44;", ",")
                .replace("&#91;", "[")
                .replace("&#93;", "]");
    }

    /**
     * 解码纯文本段落中的转义字符 (OneBot 标准中纯文本也可能有转义)
     */
    private static String decodeCQText(String text) {
        if (text == null) return "";
        return text.replace("&amp;", "&")
                .replace("&#91;", "[")
                .replace("&#93;", "]");
    }

}