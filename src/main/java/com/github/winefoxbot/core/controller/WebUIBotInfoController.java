package com.github.winefoxbot.core.controller;

import com.github.winefoxbot.core.model.entity.ShiroBots;
import com.github.winefoxbot.core.model.vo.common.Result;
import com.github.winefoxbot.core.model.vo.webui.resp.BotInfoResponse;
import com.github.winefoxbot.core.service.shiro.ShiroBotsService;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.core.BotContainer;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2026-01-18-21:08
 */
@RestController
@RequestMapping("/api/bot")
@RequiredArgsConstructor
public class WebUIBotInfoController {
    private final BotContainer botContainer;
    private final ShiroBotsService shiroBotsService;

    @GetMapping("/info/{id}")
    public Result<BotInfoResponse> getBotInfo(@PathVariable Long id) {
        Bot bot = botContainer.robots.get(id);
        if (bot == null) {
            return Result.error("没有找到对应的机器人");
        }
        ShiroBots botInfo = shiroBotsService.getById(id);
        return Result.success(new BotInfoResponse(botInfo.getBotId(),botInfo.getNickname(),botInfo.getAvatarUrl()));
    }

    @GetMapping("/avaliable")
    public List<Long> getAvaliableBots() {
        return botContainer.robots.values().stream().map(Bot::getSelfId).toList();
    }

}