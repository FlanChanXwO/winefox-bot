package com.github.winefoxbot.core.plugins;

import com.github.winefoxbot.core.service.shiro.ShiroMessagesService;
import com.mikuac.shiro.annotation.PrivateMsgDeleteNoticeHandler;
import com.mikuac.shiro.annotation.common.Order;
import com.mikuac.shiro.annotation.common.Shiro;
import com.mikuac.shiro.dto.event.notice.GroupMsgDeleteNoticeEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2026-01-07-18:14
 */
@Slf4j
@Shiro
@Component
@RequiredArgsConstructor
public class PrivateEventListenerPlugin {
    private final ShiroMessagesService shiroMessagesService;

    @PrivateMsgDeleteNoticeHandler
    @Order(1)
    public void handlePrivateMessageDelete(GroupMsgDeleteNoticeEvent event) {
        Integer messageId = event.getMessageId();
        shiroMessagesService.removeByMessageId(messageId);
    }

}