package com.github.winefoxbot.plugins.setu.aitools;

import com.github.winefoxbot.core.model.enums.MessageType;
import com.github.winefoxbot.plugins.setu.service.SetuService;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.core.BotContainer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

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
    private final BotContainer botContainer;

    public record SetuRequest(
            @ToolParam(required = true, description = "调用该工具所需的uid，需要从json消息的uid字段中获取")
            Long userId,
            @ToolParam(required = true, description = "调用该工具所需的session_id，需要从json消息的session_id字段中获取")
            Long sessionId,
            @ToolParam(required = true, description = "调用该工具所需的message_type，需要从json消息的message_type字段中获取,该参数必须为小写")
            String messageType,
            @ToolParam(required = false, description = "图片标签或关键词，例如'白丝'、'黑丝'、'碧蓝档案'等。如果用户没有指定，则为空。")
            String tag
    ) {}

    public record SetuResponse(
            @ToolParam(description = "是否调用工具成功：true:成功 false:失败")  Boolean success,
            @ToolParam(description = "错误信息") String message) {}

    @Bean("randomSetuTool")
    @Description("随机获取一张福利图片/色图/涩图。当用户想要看图片、发福利、看来点色图时调用此功能。支持指定标签。")
    public Function<SetuRequest, SetuResponse> randomSetuTool() {
        return request -> {
            try {
                // 获取任意一个在线的 Bot 实例
                Optional<Bot> botOpt = botContainer.robots.values().stream().findFirst();
                if (botOpt.isEmpty()) {
                    return new SetuResponse(false, "Bot实例未找到，无法发送图片");
                }
                Bot bot = botOpt.get();

                Long configUserId = request.userId();
                MessageType messageType = MessageType.fromValue(request.messageType.toLowerCase());
                Long groupContextId = (messageType == MessageType.GROUP) ? request.sessionId() : null;

                // 调用 Service 新增的 ID 重载方法
                setuService.processSetuRequest(bot, configUserId, groupContextId, request.tag());

                return new SetuResponse(true, "已成功触发图片发送请求");
            } catch (Exception e) {
                log.error("AI工具调用SetuService失败", e);
                return new SetuResponse(false, "调用失败: " + e.getMessage());
            }
        };
    }
}
