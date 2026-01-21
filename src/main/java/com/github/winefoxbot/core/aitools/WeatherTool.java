package com.github.winefoxbot.core.aitools;

import cn.hutool.http.HttpStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.io.IOException;
import java.util.function.Function;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2026-01-21-20:01
 */
@Configuration(proxyBeanMethods = false)
@Slf4j
@RequiredArgsConstructor
public class WeatherTool {
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    private final static String WEATHER_API_BASE_URL = "https://60s.viki.moe/v2/weather";

    public record WeatherQueryRequest(
            @ToolParam(required = true, description = "查询天气所需的地点，例如城市名称，如果用户没有提供，则你应该传递参数'北京'")
            String location
    ) {}

    public record WeatherQueryResponse(
            Boolean success,
            String weatherInfo,
            String message
    ) {}

    @Bean("weatherGetTool")
    @Description("""
            获取天气信息通过地点参数。
            当用户询问天气情况时，调用此工具以获取指定地点的天气信息。
            """)
    public Function<WeatherQueryRequest, WeatherQueryResponse> weatherGetTool() {
        return weatherQueryRequest -> {
            log.info("AI调用天气查询工具，地点：{}", weatherQueryRequest.location);
            HttpUrl.Builder builder = HttpUrl.parse(WEATHER_API_BASE_URL).newBuilder();
            builder.addQueryParameter("query", weatherQueryRequest.location);
            builder.addQueryParameter("encoding", "json");
            HttpUrl url = builder.build();
            Request request = new Request.Builder().url(url).get().build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    ResponseBody body = response.body();
                    if (body == null) {
                        return new WeatherQueryResponse(false,null,"未能获取到天气信息");
                    }
                    String data = body.string();
                    JsonNode jsonNode = objectMapper.readTree(data);
                    int code = jsonNode.get("code").asInt();
                    if (code != HttpStatus.HTTP_OK) {
                        return new WeatherQueryResponse(false,null,"获取天气信息失败，请检查城市名称拼写是否正确");
                    }
                    JsonNode weatherData = jsonNode.get("data");
                    return new WeatherQueryResponse(true,weatherData.toString(),"成功获取天气信息");
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return null;
        };
    }
}