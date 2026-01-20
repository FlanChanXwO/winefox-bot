package com.github.winefoxbot.core.service.plugin.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.winefoxbot.core.mapper.WinefoxBotAppConfigMapper;
import com.github.winefoxbot.core.model.entity.WinefoxBotPluginConfig;
import com.github.winefoxbot.core.service.plugin.WinefoxBotPluginConfigService;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
* @author FlanChan
* @description 针对表【winefox_bot_app_config】的数据库操作Service实现
* @createDate 2026-01-06 00:40:22
*/
@Service
public class WinefoxBotPluginConfigServiceImpl extends ServiceImpl<WinefoxBotAppConfigMapper, WinefoxBotPluginConfig>
    implements WinefoxBotPluginConfigService {
    /**
     * 根据唯一键 (scope, scope_id, config_key) 查找配置
     * @param scope     范围
     * @param scopeId   范围ID
     * @param configKey 配置键
     * @return Optional<WinefoxBotAppConfig>
     */
    @Override
    public Optional<WinefoxBotPluginConfig> findByUniqueKey(String scope, String scopeId, String configKey) {
        return Optional.ofNullable(this.getOne(
                new QueryWrapper<WinefoxBotPluginConfig>()
                        .eq("scope", scope)
                        .eq("scope_id", scopeId)
                        .eq("config_key", configKey)
        ));
    }
}




