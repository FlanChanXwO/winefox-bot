package com.github.winefoxbot.core.plugins;

import com.github.winefoxbot.core.service.shiro.ShiroFriendRequestsService;
import com.github.winefoxbot.core.service.shiro.ShiroFriendsService;
import com.mikuac.shiro.annotation.FriendAddNoticeHandler;
import com.mikuac.shiro.annotation.FriendAddRequestHandler;
import com.mikuac.shiro.annotation.common.Shiro;
import com.mikuac.shiro.dto.event.notice.FriendAddNoticeEvent;
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
    private final ShiroFriendRequestsService shiroFriendRequestsService;

    @FriendAddNoticeHandler
    public void handleFriendAddNotice(FriendAddNoticeEvent event) {
        Long userId = event.getUserId();
        Long selfId = event.getSelfId();
        log.info("Bot: {} 与用户 {} 成为好友", selfId, userId);
        boolean saved = shiroFriendsService.saveOrUpdateFriend(event);
        log.info("好友信息保存状态: {} for bot: {}", saved ? "成功" : "失败", selfId);
    }

    @FriendAddRequestHandler
    public void handleFriendAddRequest(FriendAddRequestEvent event) {
        Long userId = event.getUserId();
        Long selfId = event.getSelfId();
        log.info("从Bot: {} 收到好友请求来自用户: {}", selfId, userId);
        boolean saved = shiroFriendRequestsService.saveFriendAddRequest(event);
        log.info("好友请求保存状态: {} for bot: {}", saved ? "成功" : "失败", selfId);
    }


}