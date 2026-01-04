package com.github.winefoxbot.plugins;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.winefoxbot.service.chat.DeepSeekService;
import com.mikuac.shiro.annotation.GroupMessageHandler;
import com.mikuac.shiro.annotation.MessageHandlerFilter;
import com.mikuac.shiro.annotation.common.Shiro;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import com.mikuac.shiro.enums.AtEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 关键词触发插件
 *
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-09-17:19
 */
@Shiro
@Component
@Slf4j
@RequiredArgsConstructor
public class KeywordTriggerPlugin {
    private final DeepSeekService deepSeekService;
    private final ObjectMapper objectMapper;


    @GroupMessageHandler
    @MessageHandlerFilter(at = AtEnum.NOT_NEED, cmd = "(小?酒狐).*")
    public void handleMessage(Bot bot, GroupMessageEvent event) {
        Long groupId = event.getGroupId();

        ObjectNode systemMessage = objectMapper.createObjectNode();
        systemMessage.put("sender", "system");
        systemMessage.put("uid", String.valueOf(bot.getSelfId()));
        systemMessage.put("nickname", "系统事件");
        systemMessage.put("message", "你被提到了，请根据聊天上下文进行回复，注意你是主动说话的，不要说自己是被提到的");

        String resp = deepSeekService.complete(groupId, "group", systemMessage);

        // 7. Send the response to the group
        if (resp != null && !resp.isEmpty()) {
            bot.sendGroupMsg(groupId, resp, false);
        }
    }
}