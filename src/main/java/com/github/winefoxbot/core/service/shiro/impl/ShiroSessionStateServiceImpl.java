package com.github.winefoxbot.core.service.shiro.impl;

import com.github.winefoxbot.core.service.shiro.ShiroSessionStateService;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mikuac.shiro.dto.event.Event;
import com.mikuac.shiro.dto.event.message.AnyMessageEvent;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import com.mikuac.shiro.dto.event.message.PrivateMessageEvent;
import com.mikuac.shiro.dto.event.notice.PokeNoticeEvent;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 会话状态管理服务
 * <p>
 * 用于管理用户在不同会话（私聊/群聊）中的状态，例如是否处于“命令模式”。
 * 当用户触发一个需要后续指令（如“下一页”）的插件时，可以进入命令模式。
 * 在此模式下，AI聊天等通用插件会暂时忽略该用户的消息，直到命令模式结束。
 *
 * @author FlanChanXwO (Copilot)
 * @since 2025-12-23
 */
@Service
public class ShiroSessionStateServiceImpl implements ShiroSessionStateService {

    /**
     * 存储处于“命令模式”的会话
     * Key: sessionKey (唯一标识用户和其所在的聊天窗口)
     * Value: 一个占位对象
     * 使用 Guava Cache 来实现自动过期功能，避免状态被永久持有。
     */
    private final Cache<String, Object> commandModeCache = CacheBuilder.newBuilder()
            .expireAfterWrite(60, TimeUnit.SECONDS) // 用户进入命令模式后，状态持续60秒
            .build();

    private static final Object PLACEHOLDER = new Object();

    /**
     * 生成当前消息上下文的唯一会话 Key
     *
     * @param event 消息事件
     * @return sessionKey
     */
    @Override
    public String getSessionKey(GroupMessageEvent event) {
        Long groupId = event.getGroupId();
        Long userId = event.getUserId();
        // 群聊场景的 Key
        return "group_" + groupId + "_" + userId;
    }

    /**
     * 生成当前消息上下文的唯一会话 Key
     *
     * @param event 消息事件
     * @return sessionKey
     */
    @Override
    public String getSessionKey(PrivateMessageEvent event) {
        Long userId = event.getUserId();
        // 私聊场景的 Key
        return "private_" + userId;
    }

    /**
     * 生成当前消息上下文的唯一会话 Key
     *
     * @param event 消息事件
     * @return sessionKey
     */
    @Override
    public String getSessionKey(AnyMessageEvent event) {
        Long groupId = event.getGroupId();
        Long userId = event.getUserId();
        if (groupId != null) {
            // 群聊场景的 Key
            return getSessionKey((GroupMessageEvent) event);
        } else {
            // 私聊场景的 Key
            return "private_" + userId;
        }
    }


    /**
     * 生成当前消息上下文的唯一会话 Key
     *
     * @param event 消息事件
     * @return sessionKey
     */
    @Override
    public String getSessionKey(PokeNoticeEvent event) {
        Long groupId = event.getGroupId();
        Long userId = event.getUserId();
        if (groupId != null) {
            // 群聊场景的 Key
            return "group_" + groupId + "_" + userId;
        } else {
            // 私聊场景的 Key
            return "private_" + userId;
        }
    }

    /**
     * 生成当前消息上下文的唯一会话 Key
     *
     * @param event 消息事件
     * @return sessionKey
     */
    @Override
    public String getSessionKey(Event event) {
        return switch (event) {
            case AnyMessageEvent e -> getSessionKey(e);
            case GroupMessageEvent e -> getSessionKey(e);
            case PrivateMessageEvent e -> getSessionKey(e);
            case PokeNoticeEvent e -> getSessionKey(e);
            default -> throw new RuntimeException("Unsupported event type: " + event.getClass().getName());
        };
    }

    @Override
    public String getSessionState(String sessionKey) {
        return isInCommandMode(sessionKey) ? "COMMAND_MODE" : "";
    }

    /**
     * 让当前会话进入“命令模式”
     *
     * @param sessionKey 会话Key
     */
    @Override
    public void enterCommandMode(String sessionKey) {
        commandModeCache.put(sessionKey, PLACEHOLDER);
    }

    /**
     * 让当前会话退出“命令模式”
     *
     * @param sessionKey 会话Key
     */
    @Override
    public void exitCommandMode(String sessionKey) {
        commandModeCache.invalidate(sessionKey);
    }

    /**
     * 检查当前会话是否处于“命令模式”
     *
     * @param sessionKey 会话Key
     * @return 如果在命令模式中，返回 true
     */
    @Override
    public boolean isInCommandMode(String sessionKey) {
        return commandModeCache.getIfPresent(sessionKey) != null;
    }
}