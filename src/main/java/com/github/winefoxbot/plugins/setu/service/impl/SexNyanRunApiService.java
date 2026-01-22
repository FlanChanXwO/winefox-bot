package com.github.winefoxbot.plugins.setu.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.github.winefoxbot.core.exception.common.BusinessException;
import com.github.winefoxbot.plugins.setu.model.dto.SetuProviderRequest;
import com.github.winefoxbot.plugins.setu.service.SetuImageProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * SexNyanRun API 服务实现
 * 文档: https://sex.nyan.run/api/v2/
 */
@Slf4j
@Service("sexNyanRunApiService")
@RequiredArgsConstructor
public class SexNyanRunApiService implements SetuImageProvider {

    private final OkHttpClient httpClient;

    private static final String API_URL = "https://sex.nyan.run/api/v2/";
    private static final int MAX_SUPPORTED_NUM = 10;

    @Override
    public List<String> fetchImages(SetuProviderRequest request) {
        List<String> keywords = request.keywords();
        int num = request.num();
        boolean r18 = request.r18();

        // 接口契约：如果该实现类不支持该数量，应当抛出异常
        if (num < 1 || num > MAX_SUPPORTED_NUM) {
            throw new BusinessException("SexNyanRun API 限制单次获取数量为 1-%d 张".formatted(MAX_SUPPORTED_NUM));
        }

        HttpUrl.Builder urlBuilder = Objects.requireNonNull(HttpUrl.parse(API_URL)).newBuilder();

        // 组装参数
        urlBuilder.addQueryParameter("r18", String.valueOf(r18));
        urlBuilder.addQueryParameter("num", String.valueOf(num));

        // 如果有关键词，映射到 keywords 参数（支持模糊搜索标题、作者、标签）
        if (!keywords.isEmpty()) {
            for (String keyword : keywords) {
                urlBuilder.addQueryParameter("keywords", keyword);
            }
        }
        String requestUrl = urlBuilder.build().toString();
        log.debug("Requesting Setu API: {}", requestUrl);

        Request httpRequest = new Request.Builder().url(requestUrl).get().build();

        try (Response response = httpClient.newCall(httpRequest).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                log.error("API 请求失败 Code: {}, URL: {}", response.code(), requestUrl);
                throw new BusinessException("API 请求失败，状态码: " + response.code());
            }

            String jsonBody = response.body().string();
            return parseResponse(jsonBody);

        } catch (IOException e) {
            log.error("API 网络请求异常", e);
            throw new BusinessException("连接色图服务器失败: " + e.getMessage());
        }
    }

    /**
     * 解析 SexNyanRun 的 JSON 响应
     */
    private List<String> parseResponse(String jsonBody) {
        try {
            JSONObject json = JSONUtil.parseObj(jsonBody);
            //根据 API 通常的返回格式，这里假设 data 字段包含图片列表
            JSONArray data = json.getJSONArray("data");

            if (CollUtil.isEmpty(data)) {
                return Collections.emptyList();
            }

            List<String> urls = new ArrayList<>();
            for (Object item : data) {
                if (item instanceof JSONObject imgObj) {
                    // 优先取 url
                    String url = imgObj.getStr("url");
                    if (url != null) {
                        urls.add(url);
                    }
                }
            }
            return urls;
        } catch (Exception e) {
            log.error("解析 API 响应失败. Body: {}", jsonBody, e);
            throw new BusinessException("解析图片数据失败");
        }
    }
}
