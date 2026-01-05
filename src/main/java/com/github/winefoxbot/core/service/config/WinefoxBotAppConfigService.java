package com.github.winefoxbot.core.service.config;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.github.winefoxbot.core.model.entity.WinefoxBotAppConfig;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.Optional;

/**
* @author FlanChan
* @description 针对表【winefox_bot_app_config】的数据库操作Service
* @createDate 2026-01-06 00:40:22
*/
public interface WinefoxBotAppConfigService extends IService<WinefoxBotAppConfig> {
    /**
     * 根据唯一键 (scope, scope_id, config_key) 查找配置
     * @param scope     范围
     * @param scopeId   范围ID
     * @param configKey 配置键
     * @return Optional<WinefoxBotAppConfig>
     */
    Optional<WinefoxBotAppConfig> findByUniqueKey(String scope, String scopeId, String configKey);
}
