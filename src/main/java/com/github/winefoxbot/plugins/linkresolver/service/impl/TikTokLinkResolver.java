package com.github.winefoxbot.plugins.linkresolver.service.impl;

import com.github.winefoxbot.plugins.linkresolver.service.LinkResolverService;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Slf4j
@Service
public class TikTokLinkResolver implements LinkResolverService {

    private static final String REGEX_STR = "(https?://)?(www\\.)?(douyin\\.com|iesdouyin\\.com)/.+";
    private static final Pattern REGEX = Pattern.compile(REGEX_STR, Pattern.CASE_INSENSITIVE);

    @Override
    public Pattern getRegex() {
        return REGEX;
    }

    @Override
    public void resolve(Bot bot, GroupMessageEvent event, String match) {
        // TODO: 实现抖音解析逻辑
        log.info("TikTok link detected: {}", match);
    }

    @Override
    public String getCanonicalId(String url) {
        return url;
    }
}
