package com.github.winefoxbot.plugins.linkresolver.service;

import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;

import java.util.regex.Pattern;

/**
 * 链接解析服务接口
 */
public interface LinkResolverService {

    /**
     * 获取该解析器支持的正则表达式
     * @return Pattern
     */
    Pattern getRegex();

    /**
     * 执行解析逻辑
     * @param bot Bot实例
     * @param event 群消息事件
     * @param match 匹配到的链接或文本
     */
    void resolve(Bot bot, GroupMessageEvent event, String match);
}
