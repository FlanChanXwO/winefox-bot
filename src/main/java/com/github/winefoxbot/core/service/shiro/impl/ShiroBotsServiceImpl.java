package com.github.winefoxbot.core.service.shiro.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.winefoxbot.core.model.entity.ShiroBots;
import com.github.winefoxbot.core.service.shiro.ShiroBotsService;
import com.github.winefoxbot.core.mapper.ShiroBotsMapper;
import com.mikuac.shiro.common.utils.ShiroUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.action.common.ActionData;
import com.mikuac.shiro.dto.action.response.LoginInfoResp;
import com.mikuac.shiro.dto.action.response.StrangerInfoResp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
* @author FlanChan
* @description 针对表【shiro_bots(存储机器人账号自身的信息)】的数据库操作Service实现
* @createDate 2026-01-07 18:24:07
*/
@Service
@Slf4j
public class ShiroBotsServiceImpl extends ServiceImpl<ShiroBotsMapper, ShiroBots>
    implements ShiroBotsService{

    @Override
    public boolean saveOrUpdateBotInfo(Bot bot) {
        ShiroBots shiroBot = createShiroBot(bot);
        log.info("保存或更新 Bot {} 的信息到数据库...", bot.getSelfId());
        return this.saveOrUpdate(shiroBot);
    }


    private ShiroBots createShiroBot(Bot bot) {
        ActionData<LoginInfoResp> loginInfoResp = bot.getLoginInfo();
        String nickname;
        String avatarUrl = ShiroUtils.getUserAvatar(bot.getSelfId(),0);
        if (loginInfoResp.getRetCode() == 0) {
            LoginInfoResp data = loginInfoResp.getData();
            nickname = data.getNickname();
        } else {
            nickname = String.valueOf(bot.getSelfId());
        }

        ShiroBots shiroBots = new ShiroBots();
        shiroBots.setBotId(bot.getSelfId());
        shiroBots.setNickname(nickname);
        shiroBots.setAvatarUrl(avatarUrl);
        shiroBots.setLastUpdated(LocalDateTime.now());
        return shiroBots;
    }
}




