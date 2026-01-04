package com.github.winefoxbot.core.service.config;

import com.github.winefoxbot.core.model.entity.AppConfig;

import java.util.List;
import java.util.Optional;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-16-0:32
 */
public interface ConfigService {
    boolean getGroupConfigAsBoolean(long groupId, String key, boolean defaultValue);

    void setGroupConfig(long groupId, String group, String key, Object value, String description);

    List<AppConfig> getConfigsByGroup(String group, String scope, String scopeId);

    Optional<String> getConfigValue(String scope, String scopeId, String key);

    void setConfigValue(String group, String scope, String scopeId, String key, String value, String description);
}
