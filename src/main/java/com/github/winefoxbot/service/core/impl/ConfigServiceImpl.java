package com.github.winefoxbot.service.core.impl;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.github.winefoxbot.mapper.AppConfigMapper;
import com.github.winefoxbot.model.entity.AppConfig;
import com.github.winefoxbot.service.core.ConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ConfigServiceImpl implements ConfigService {

    private final AppConfigMapper configMapper;

    private static final String SCOPE_GROUP = "group";

    /**
     * 获取一个群组的布尔配置项。
     * @param groupId 群号
     * @param key 配置键
     * @param defaultValue 如果未配置，返回的默认值
     * @return 配置值
     */
    @Override
    public boolean getGroupConfigAsBoolean(long groupId, String key, boolean defaultValue) {
        return getConfigValue(SCOPE_GROUP, String.valueOf(groupId), key)
                .map(Boolean::parseBoolean)
                .orElse(defaultValue);
    }

    /**
     * 设置一个群组的配置项。
     * @param groupId 群号
     * @param group 配置分组名
     * @param key 配置键
     * @param value 配置值
     * @param description 配置项描述
     */
    @Override
    public void setGroupConfig(long groupId, String group, String key, Object value, String description) {
        setConfigValue(group, SCOPE_GROUP, String.valueOf(groupId), key, String.valueOf(value), description);
    }

    /**
     * 【新增】按组获取指定范围的所有配置项，为WebUI准备。
     * @param group 配置分组名
     * @param scope 配置范围 ('group', 'global', etc.)
     * @param scopeId 范围ID ('群号', 'default', etc.)
     * @return 配置实体列表
     */
    @Override
    public List<AppConfig> getConfigsByGroup(String group, String scope, String scopeId) {
        return configMapper.selectList(new LambdaQueryWrapper<AppConfig>()
                .eq(AppConfig::getConfigGroup, group)
                .eq(AppConfig::getScope, scope)
                .eq(AppConfig::getScopeId, scopeId));
    }

    /**
     * 核心获取方法
     */
    @Override
    public Optional<String> getConfigValue(String scope, String scopeId, String key) {
        AppConfig config = configMapper.selectOne(new LambdaQueryWrapper<AppConfig>()
                .eq(AppConfig::getScope, scope)
                .eq(AppConfig::getScopeId, scopeId)
                .eq(AppConfig::getConfigKey, key));
        
        return Optional.ofNullable(config).map(AppConfig::getConfigValue);
    }

    /**
     * 核心设置方法 (UPSERT: Update or Insert) - 已更新
     */
    @Override
    public void setConfigValue(String group, String scope, String scopeId, String key, String value, String description) {
        // 尝试更新
        int updatedRows = configMapper.update(null, new LambdaUpdateWrapper<AppConfig>()
                .eq(AppConfig::getScope, scope)
                .eq(AppConfig::getScopeId, scopeId)
                .eq(AppConfig::getConfigKey, key)
                .set(AppConfig::getConfigValue, value)
                .set(AppConfig::getConfigGroup, group)); // 同时确保group也更新
        
        // 如果影响行数为0，则说明记录不存在，执行插入
        if (updatedRows == 0) {
            AppConfig newConfig = new AppConfig()
                .setConfigGroup(group)
                .setScope(scope)
                .setScopeId(scopeId)
                .setConfigKey(key)
                .setConfigValue(value)
                .setDescription(description);
            configMapper.insert(newConfig);
        }
    }
}
