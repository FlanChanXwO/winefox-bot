package com.github.winefoxbot.plugins.fortune.aitools;

import com.github.winefoxbot.core.model.enums.MessageType;
import com.github.winefoxbot.plugins.fortune.model.vo.FortuneRenderVO;
import com.github.winefoxbot.plugins.fortune.service.FortuneDataService;
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
 * AI工具类，用于查询今日运势
 * 当AI认为用户想要查询今日运势时，会调用此工具
 *
 * @author FlanChan
 */
@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
@Slf4j
public class FortuneTool {

    private final BotContainer botContainer;
    private final FortuneDataService fortuneDataService;

    // 1. 定义请求参数结构
    public record FortuneRequest(
            @ToolParam(required = false, description = "调用该工具所需的uid，需要从json消息的uid字段中获取")
            Long userId,
            @ToolParam(required = false, description = "调用该工具所需的session_id，需要从json消息的session_id字段中获取")
            Long sessionId,
            @ToolParam(description = "调用该工具所需的message_type，需要从json消息的message_type字段中获取,该参数必须为小写")
            String messageType
    ) {}

    public record FortuneResponse(
            @ToolParam(description = "是否调用工具成功：true:成功 false:失败")  Boolean success,
            @ToolParam(description = "错误信息") String message,
            @ToolParam(description = "今日运势数据") FortuneContent fortuneContent
    ) {}

    public record FortuneContent(
            @ToolParam(description = "运势标题 (大吉/中吉...)") String title,
            @ToolParam(description = "运势描述") String description,
            @ToolParam(description = "额外描述 (宜/忌等)") String extraMessage,
            @ToolParam(description = "星星数量") int starCount
    ) {}


    @Bean("fortuneGetTool")
    @Description("""
    Get the daily fortune (今日运势).
    
    TRIGGER RULES:
    1. ONLY invoke this tool when the user EXPLICITLY asks for 'fortune', 'luck', 'fortune telling', or '今日运势'.
    2. DO NOT invoke this tool for ambiguous inputs like '?', 'hello', 'what', or simple greetings.
    3. If the user input is just punctuation or unclear, reply with text only.
    
    Function: Returns the fortune content text and triggers an asynchronous image send.
    """)
    public Function<FortuneRequest,FortuneResponse> fortuneGetTool() {
        return req -> {
            Optional<Bot> botOptional = botContainer.robots.values().stream().findFirst();
            if (botOptional.isEmpty()) {
                return new FortuneResponse(false, "没有可用的机器人实例", null);
            }
            Bot bot = botOptional.get();

            Long userId = req.userId();
            Long sessionId = req.sessionId();
            MessageType messageType = MessageType.fromValue(req.messageType().toLowerCase());

            log.info("AI工具请求今日运势，userId: {}, sessionId: {}, messageType: {}", userId, sessionId, messageType);

            // 尝试获取用户显示名称
            String displayName = "指挥官";
            try {
                if (messageType == MessageType.GROUP && sessionId != null) {
                    var info = bot.getGroupMemberInfo(sessionId, userId, true);
                    if (info != null && info.getData() != null) {
                        String card = info.getData().getCard();
                        displayName = (card != null && !card.isEmpty()) ? card : info.getData().getNickname();
                    }
                } else {
                    var info = bot.getStrangerInfo(userId, true);
                    if (info != null && info.getData() != null) {
                        displayName = info.getData().getNickname();
                    }
                }
            } catch (Exception e) {
                log.warn("获取用户信息失败，使用默认名称", e);
            }

            try {
                // 获取运势数据 VO
                FortuneRenderVO vo = fortuneDataService.getFortuneRenderVO(userId, displayName);

                // 异步发送图片任务
                new Thread(() -> {
                    Long groupId = (messageType == MessageType.GROUP) ? sessionId : null;
                    fortuneDataService.sendFortuneImage(bot, userId, groupId, messageType, vo);
                }).start();

                // 返回运势文本内容给AI
                FortuneContent content = new FortuneContent(
                        vo.title(),
                        vo.description(),
                        vo.extraMessage(),
                        vo.starCount()
                );
                return new FortuneResponse(true, "运势获取成功，运势卡片已发送", content);

            } catch (Exception e) {
                log.error("AI工具获取运势失败", e);
                return new FortuneResponse(false, "系统内部错误: " + e.getMessage(), null);
            }
        };
    }
}
