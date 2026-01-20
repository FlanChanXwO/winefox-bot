package com.github.winefoxbot.core.service.shiro;

import com.baomidou.mybatisplus.extension.service.IService;
import com.github.winefoxbot.core.model.entity.ShiroBots;
import com.mikuac.shiro.core.Bot;

/**
* @author FlanChan
* @description 针对表【shiro_bots(存储机器人账号自身的信息)】的数据库操作Service
* @createDate 2026-01-07 18:24:07
*/
public interface ShiroBotsService extends IService<ShiroBots> {

    /**
     * 保存或更新Bot信息
     * @param bot
     * @return
     */
    boolean saveOrUpdateBotInfo(Bot bot);
}
