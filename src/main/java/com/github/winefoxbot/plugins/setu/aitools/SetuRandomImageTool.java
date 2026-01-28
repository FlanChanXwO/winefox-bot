package com.github.winefoxbot.plugins.setu.aitools;

import com.github.winefoxbot.core.context.BotContext;
import com.github.winefoxbot.core.model.enums.common.MessageType;
import com.github.winefoxbot.core.service.common.SmartTagService;
import com.github.winefoxbot.plugins.setu.service.SetuService;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.core.BotContainer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * AI工具类，用于调用 SetuPlugin 的随机图片功能
 *
 * @author Winefox
 */
@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
@Slf4j
public class SetuRandomImageTool {

    private final SetuService setuService;
    private final SmartTagService tagService;

    public record SetuRequest(
            @ToolParam(required = false, description = "图片标签或关键词，例如'白丝'、'黑丝'、'碧蓝档案'等。如果用户没有指定，则为空。")
            String keyword,
            @ToolParam(required = false,description = "请求图片数量，默认为1，最大不得超过10个")
            int num
    ) {}

    public record SetuResponse(
            @ToolParam(description = "是否调用工具成功：true:成功 false:失败")  Boolean success,
            @ToolParam(description = "错误信息") String message) {}

    @Bean
    @Description("""
    获取一个随机的色图，可以指定标签和数量。
    当用户需要获取色图时，调用此工具以触发图片发送功能，当你请求成功时，图片已经被发送了。
    例如，用户可能会说“给我来几张白丝的图片”或“我想看一些碧蓝档案的色图”等。
    该工具接受用户ID、会话ID、消息类型、标签和数量作为参数，并返回调用结果。
    """)
    public Function<SetuRequest, SetuResponse> randomSetuTool() {
        return request -> {
            log.info("AI调用随机色图工具，标签：{}，数量：{}", request.keyword(), request.num);
            try {
                if (request.num <= 0 || request.num > 10) {
                    return new SetuResponse(false, "请求图片数量必须在1到10之间");
                }
                List<String> searchTags = tagService.getSearchTags(request.keyword());
                setuService.handleSetuRequest(request.num, searchTags);
                return new SetuResponse(true, "已成功触发图片发送请求");
            } catch (Exception e) {
                log.error("AI工具调用SetuService失败", e);
                return new SetuResponse(false, "调用失败: " + e.getMessage());
            }
        };
    }
}
