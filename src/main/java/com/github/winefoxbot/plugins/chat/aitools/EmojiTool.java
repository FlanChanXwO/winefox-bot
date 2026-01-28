package com.github.winefoxbot.plugins.chat.aitools;

import cn.hutool.core.io.resource.ResourceUtil;
import com.github.winefoxbot.core.context.BotContext;
import com.github.winefoxbot.core.utils.Base64Utils;
import com.github.winefoxbot.plugins.chat.init.EmoteVectorStoreLoader;
import com.github.winefoxbot.plugins.chat.manager.EmoteManager;
import com.github.winefoxbot.plugins.chat.model.dto.EmoteResponse;
import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.common.utils.OneBotMedia;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import com.mikuac.shiro.dto.event.message.MessageEvent;
import com.mikuac.shiro.dto.event.message.PrivateMessageEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;
import org.springframework.core.io.Resource;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.function.Function;

/**
 * @author FlanChan
 */
@ConditionalOnBean(EmoteVectorStoreLoader.class)
@Configuration(proxyBeanMethods = false)
@Slf4j
@RequiredArgsConstructor
public class EmojiTool {

    // 定义 AI 调用工具时输入的参数结构
    public record EmoteSearchRequest(
            @ToolParam(description = "当前对话的意图、情感或关键词，用于搜索表情包。例如：'生气炸群'、'开心撒花'、'暗中观察'")
            String intentDescription
    ) {
    }

    public record EmoteSearchResponse(
            Boolean success,
            String messsage
    ) {
    }

    @Bean("searchEmoteTool")
    @Description("根据对话意图搜索最合适的表情包图片然后发送")
    public Function<EmoteSearchRequest, EmoteSearchResponse> searchEmoteTool(EmoteManager manager) {
        return request -> {

            // 直接调用分离出来的 Service 进行查询
            Bot bot = BotContext.CURRENT_BOT.get();
            MessageEvent messageEvent = BotContext.CURRENT_MESSAGE_EVENT.get();
            EmoteResponse emoteResponse = manager.searchBestMatch(request.intentDescription());
            String msg = MsgUtils.builder().img(new OneBotMedia()
                            .summary("[动画表情]")
                            .file(Base64Utils.toBase64String(ResourceUtil.readBytes("classpath:emoji/" + emoteResponse.path()))))
                    .build();
            if (messageEvent instanceof PrivateMessageEvent e) {
                bot.sendPrivateMsg(e.getUserId(), msg, false);
            } else if (messageEvent instanceof GroupMessageEvent e) {
                bot.sendGroupMsg(e.getGroupId(), msg, false);
            }
            return new EmoteSearchResponse(true, "表情包发送成功");
        };
    }
}
