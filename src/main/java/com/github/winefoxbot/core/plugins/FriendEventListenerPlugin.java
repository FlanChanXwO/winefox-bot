package com.github.winefoxbot.core.plugins;

import com.github.winefoxbot.core.service.shiro.ShiroFriendsService;
import com.github.winefoxbot.core.service.shiro.ShiroMessagesService;
import com.mikuac.shiro.annotation.FriendAddNoticeHandler;
import com.mikuac.shiro.annotation.FriendAddRequestHandler;
import com.mikuac.shiro.annotation.PrivateMsgDeleteNoticeHandler;
import com.mikuac.shiro.annotation.common.Order;
import com.mikuac.shiro.annotation.common.Shiro;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.notice.FriendAddNoticeEvent;
import com.mikuac.shiro.dto.event.notice.GroupMsgDeleteNoticeEvent;
import com.mikuac.shiro.dto.event.request.FriendAddRequestEvent;
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
public class FriendEventListenerPlugin {
    private final ShiroFriendsService shiroFriendsService;

    @FriendAddNoticeHandler
    @Order(1)
    public void handleFriendAddNotice(FriendAddNoticeEvent event) {
        Long userId = event.getUserId();
        Long selfId = event.getSelfId();
        if (shiroFriendsService.saveOrUpdateFriend(event)) {
            log.info("Saved new friend: {} for bot: {}", userId, selfId);
        } else {
            log.warn("Failed to save new friend: {} for bot: {}", userId, selfId);
        }
    }

    @FriendAddRequestHandler
    public void handleFriendAddRequest(FriendAddRequestEvent event) {
        Long userId = event.getUserId();
        Long selfId = event.getSelfId();
        log.info("Received friend add request from user: {} for bot: {}", userId, selfId);
    }

}