package com.github.winefoxbot.plugins.setu.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.github.winefoxbot.core.exception.common.BusinessException;
import com.github.winefoxbot.plugins.setu.model.dto.SetuProviderRequest;
import com.github.winefoxbot.plugins.setu.service.SetuImageProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

/**
 * Lolicon API V2 实现
 * 文档: https://api.lolicon.app/setu/v2
 * @author FlanChan
 */
@Slf4j
@Service("loliconApiService") // 给 Bean 起个名，方便后续指定
@RequiredArgsConstructor
public class LoliconApiService implements SetuImageProvider {

    private final OkHttpClient httpClient;
    private static final String API_URL = "https://api.lolicon.app/setu/v2";

    @Override
    public List<String> fetchImages(SetuProviderRequest request) {
        // 1. 组装请求参数
        HttpUrl.Builder urlBuilder = HttpUrl.parse(API_URL).newBuilder();

        // 基础参数映射
        urlBuilder.addQueryParameter("r18", String.valueOf(request.r18() ? 1 : 0));
        urlBuilder.addQueryParameter("num", String.valueOf(request.num()));
        
        // 强制排除 AI 作品 (关键需求)
        urlBuilder.addQueryParameter("excludeAI", String.valueOf(true));

        // 处理关键词/Tag
        if (!request.keywords().isEmpty()) {
            // 这里既可以传给 tag 也可以传给 keywords，文档建议 tag 匹配更准
            // 如果你的 keywords 包含 "|" 或 "&" 逻辑符，必须用 tag 数组
            for (String keyword : request.keywords()) {
                urlBuilder.addQueryParameter("tag", keyword);
            }
        }

        // 处理额外参数 (extraParams)
        // 这里就是你传递 size, proxy, aspectRatio, uid 等参数的地方
        if (CollUtil.isNotEmpty(request.extraParams())) {
            request.extraParams().forEach((key, value) -> {
                // 防止核心参数被覆盖，做一个简单的过滤
                if (!List.of("r18", "num", "excludeAI").contains(key)) {
                    urlBuilder.addQueryParameter(key, String.valueOf(value));
                }
            });
        }

        HttpUrl url = urlBuilder.build();

        log.info("实际请求 Lolicon API URL: {}", url);

        // 2. 发送 GET 请求
        Request apiRequest = new Request.Builder()
                .url(url)
                .get()
                .build();

        log.debug("Calling Lolicon API: {}", url);

        try (Response response = httpClient.newCall(apiRequest).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                log.error("Lolicon API 请求失败 Code: {}", response.code());
                throw new BusinessException("API请求失败: " + response.code());
            }

            String responseStr = response.body().string();
            return parseResponse(responseStr);

        } catch (IOException e) {
            log.error("Lolicon API 网络异常", e);
            throw new BusinessException("连接色图服务器失败");
        }
    }

    private List<String> parseResponse(String jsonStr) {
        try {
            JSONObject result = JSONUtil.parseObj(jsonStr);
            JSONArray data = result.getJSONArray("data");
            
            if (CollUtil.isEmpty(data)) {
                return Collections.emptyList();
            }

            List<String> urls = new ArrayList<>();
            for (Object item : data) {
                JSONObject imgData = (JSONObject) item;
                JSONObject urlsObj = imgData.getJSONObject("urls");
                if (urlsObj != null) {
                    // 优先获取 original，如果想获取其他尺寸，可以在 extraParams 里传 size
                    // 但 API 返回结构里 urls 对象通常包含了所有尺寸的链接
                    String url = urlsObj.getStr("original");
                    if (StrUtil.isBlank(url)) {
                        // 降级尝试获取 regular
                        url = urlsObj.getStr("regular");
                    }
                    if (StrUtil.isNotBlank(url)) {
                        urls.add(url);
                    }
                }
            }
            return urls;
        } catch (Exception e) {
            log.error("解析 Lolicon API 响应失败: {}", jsonStr, e);
            throw new BusinessException("图片数据解析异常");
        }
    }
}
