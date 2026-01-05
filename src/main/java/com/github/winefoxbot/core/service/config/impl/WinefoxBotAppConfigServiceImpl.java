package com.github.winefoxbot.core.service.config.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.winefoxbot.core.mapper.WinefoxBotAppConfigMapper;
import com.github.winefoxbot.core.model.entity.WinefoxBotAppConfig;
import com.github.winefoxbot.core.service.config.WinefoxBotAppConfigService;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
* @author FlanChan
* @description 针对表【winefox_bot_app_config】的数据库操作Service实现
* @createDate 2026-01-06 00:40:22
*/
@Service
public class WinefoxBotAppConfigServiceImpl extends ServiceImpl<WinefoxBotAppConfigMapper, WinefoxBotAppConfig>
    implements WinefoxBotAppConfigService{
    /**
     * 根据唯一键 (scope, scope_id, config_key) 查找配置
     * @param scope     范围
     * @param scopeId   范围ID
     * @param configKey 配置键
     * @return Optional<WinefoxBotAppConfig>
     */
    @Override
    public Optional<WinefoxBotAppConfig> findByUniqueKey(String scope, String scopeId, String configKey) {
        return Optional.ofNullable(this.getOne(
                new QueryWrapper<WinefoxBotAppConfig>()
                        .eq("scope", scope)
                        .eq("scope_id", scopeId)
                        .eq("config_key", configKey)
        ));
    }
}




