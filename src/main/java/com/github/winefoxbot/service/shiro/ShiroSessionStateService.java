package com.github.winefoxbot.service.shiro;

import com.mikuac.shiro.dto.event.Event;
import com.mikuac.shiro.dto.event.message.AnyMessageEvent;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import com.mikuac.shiro.dto.event.message.PrivateMessageEvent;
import com.mikuac.shiro.dto.event.notice.PokeNoticeEvent;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-23-12:25
 */
public interface ShiroSessionStateService {
    String getSessionKey(GroupMessageEvent event);

    String getSessionKey(PrivateMessageEvent event);

    String getSessionKey(AnyMessageEvent event);

    String getSessionKey(PokeNoticeEvent event);

    String getSessionKey(Event event);

    String getSessionState(String sessionKey);

    void enterCommandMode(String sessionKey);

    void exitCommandMode(String sessionKey);

    boolean isInCommandMode(String sessionKey);
}
