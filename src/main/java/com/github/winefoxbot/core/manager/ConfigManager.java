package com.github.winefoxbot.core.manager;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.github.winefoxbot.core.model.entity.WinefoxBotAppConfig;
import com.github.winefoxbot.core.service.config.WinefoxBotAppConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * 统一应用配置管理器
 * 封装了配置的读取、写入和优先级处理逻辑。
 * 设计目标是提供一个简单、类型安全且高效的配置访问接口。
 */
@Service
@Slf4j
@RequiredArgsConstructor // 使用 Lombok 自动注入 final 字段
public class ConfigManager {

    private final WinefoxBotAppConfigService configService;
    private final static String GLOBAL_SCOPE_ID = "default";

    public enum Scope {
        GLOBAL,
        GROUP,
        USER
    }


    /**
     * 获取指定范围的配置项，并提供类型转换。
     * 这是最核心的配置获取方法，实现了 User > Group > Global 的优先级覆盖。
     *
     * @param key       配置键，例如 "setu.r18.enabled"
     * @param userId    用户ID (可为 null)
     * @param groupId   群组ID (可为 null)
     * @param type      期望返回的类型 Class，例如 String.class, Integer.class, Boolean.class
     * @param <T>       泛型类型
     * @return 封装在 Optional 中的配置值，如果所有范围都找不到则 Optional.empty()
     */
    public <T> Optional<T> get(String key, Long userId, Long groupId, Class<T> type) {
        // 优先级 1: 查询群组配置
        if (groupId != null) {
            Optional<T> groupConfig = getConfigObject("group", groupId, key, type);
            if (groupConfig.isPresent()) {
                return groupConfig;
            } else { // 群组配置不存在时，继续查询全局配置
                return getConfigObject("global", -1L, key, type);
            }
        }

        // 优先级 2: 查询用户配置
        if (userId != null) {
            Optional<T> userConfig = getConfigObject("user", userId, key, type);
            if (userConfig.isPresent()) {
                return userConfig;
            }
        }

        // 优先级 3: 查询全局配置
        return getConfigObject("global", -1L, key, type);
    }


    /**
     * 获取配置，如果找不到则返回默认值。
     */
    public <T> T getOrDefault(String key, Long userId, Long groupId, T defaultValue) {
        @SuppressWarnings("unchecked") // 我们知道 defaultValue 的类型是 T
        Class<T> type = (Class<T>) defaultValue.getClass();
        return get(key, userId, groupId, type).orElse(defaultValue);
    }

    public String getString(String key, Long userId, Long groupId, String defaultValue) {
        return getOrDefault(key, userId, groupId, defaultValue);
    }
    
    public Integer getInt(String key, Long userId, Long groupId, Integer defaultValue) {
        // 注意：JSONB存的是数字，但反序列化为Object后可能是Integer, Long, Double等
        // 需要做更健壮的转换
        Optional<Number> value = get(key, userId, groupId, Number.class);
        return value.map(Number::intValue).orElse(defaultValue);
    }

    public Boolean getBoolean(String key, Long userId, Long groupId, Boolean defaultValue) {
        return getOrDefault(key, userId, groupId, defaultValue);
    }
    
    // --- 专用场景的简化方法 ---

    /**
     * 获取群组配置 (自动回退到全局)
     */
    public <T> T getGroupConfigOrDefault(String key, Long groupId, T defaultValue) {
        return getOrDefault(key, null, groupId, defaultValue);
    }

    /**
     * 获取全局配置
     */
     public <T> T getGlobalConfigOrDefault(String key, T defaultValue) {
        return getOrDefault(key, null, null, defaultValue);
    }


    /**
     * 从数据库获取单个配置项并进行类型转换
     * @param scope     范围 ('user', 'group', 'global')
     * @param scopeId   范围ID
     * @param key       配置键
     * @param type      目标类型
     * @return Optional<T>
     */
    private <T> Optional<T> getConfigObject(String scope, Long scopeId, String key, Class<T> type) {
        QueryWrapper<WinefoxBotAppConfig> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("scope", scope)
                .eq("scope_id", Objects.toString(scopeId))
                .eq("config_key", key);

        WinefoxBotAppConfig config = configService.getOne(queryWrapper);

        if (config == null || config.getConfigValue() == null) {
            return Optional.empty();
        }

        Object value = config.getConfigValue();

        // 1. 如果类型完全匹配，直接返回
        if (type.isInstance(value)) {
            return Optional.of(type.cast(value));
        }

        // 2. 特殊处理：如果期望是 String，但得到的是 List，则取第一个元素
        if (type.equals(String.class) && value instanceof java.util.List<?> list) {
            if (!list.isEmpty()) {
                // 取出第一个元素并转换为字符串
                return (Optional<T>) Optional.ofNullable(list.get(0)).map(String::valueOf);
            } else {
                return Optional.empty(); // 空列表，视为没有值
            }
        }

        // 3. 尝试进行数字类型转换
        if (value instanceof Number && Number.class.isAssignableFrom(type)) {
            Number numberValue = (Number) value;
            try {
                if (type == Integer.class) return Optional.of(type.cast(numberValue.intValue()));
                if (type == Long.class) return Optional.of(type.cast(numberValue.longValue()));
                if (type == Double.class) return Optional.of(type.cast(numberValue.doubleValue()));
                // 可以根据需要添加其他数字类型
            } catch (ClassCastException e) {
                // 转换失败，继续向下
            }
        }

        // 4. 最后的尝试：使用 String.valueOf 进行转换
        // 只有当期望类型是 String 时才这么做
        if (type.equals(String.class)) {
            return (Optional<T>) Optional.of(String.valueOf(value));
        }


        // 如果所有智能转换都失败，记录日志并返回空
        log.warn("Config type mismatch for key '{}'. Expected {}, but got {}. Conversion failed.",
                key, type.getSimpleName(), value.getClass().getSimpleName());

        return Optional.empty();
    }


    /**
     * 设置或更新一个配置项 (核心方法)。
     * 如果配置已存在，则更新其值；否则，创建新配置。
     *
     * @param scope       配置范围 ('global', 'group', 'user')
     * @param scopeId     范围ID ('default', '群号', 'QQ号')
     * @param key         配置键
     * @param value       配置值 (可以是任何可被Jackson序列化的对象)
     * @param description 配置描述 (可选)
     * @param groupName   配置分组名 (可选, 用于分类)
     */
    @Transactional // 建议加上事务，保证操作的原子性
    public void set(Scope scope, String scopeId, String key, Object value, String description, String groupName) {
        String scopeValue = convertScopeEnumToStringValue(scope);
        Optional<WinefoxBotAppConfig> existingConfigOpt = configService.findByUniqueKey(scopeValue, scopeId, key);

        if (existingConfigOpt.isPresent()) {
            // --- 更新现有配置 ---
            WinefoxBotAppConfig configToUpdate = existingConfigOpt.get();
            configToUpdate.setConfigValue(value);
            configToUpdate.setUpdatedAt(LocalDateTime.now());
            // 如果传入了新的描述或分组，也更新它们
            if (description != null) {
                configToUpdate.setDescription(description);
            }
            if (groupName != null) {
                configToUpdate.setConfigGroup(groupName);
            }
            configService.updateById(configToUpdate);
        } else {
            // --- 创建新配置 ---
            WinefoxBotAppConfig newConfig = new WinefoxBotAppConfig();
            newConfig.setScope(scopeValue);
            newConfig.setScopeId(scopeId);
            newConfig.setConfigKey(key);
            newConfig.setConfigValue(value);
            newConfig.setConfigGroup(groupName != null ? groupName : "未分类"); // 提供一个默认分组
            newConfig.setDescription(description);
            // createdAt 和 updatedAt 数据库有默认值，但最好也设置一下
            newConfig.setCreatedAt(LocalDateTime.now());
            newConfig.setUpdatedAt(LocalDateTime.now());
            configService.save(newConfig);
        }
    }



    public void set(Scope scope, String scopeId, String key, Object value) {
        set(scope, scopeId, key, value, null, null);
    }

    /**
     * 设置群组配置。
     * @param groupId 群号
     * @param key     配置键
     * @param value   配置值
     */
    public void setGroupConfig(String groupId, String key, Object value) {
        set(Scope.GROUP, groupId, key, value);
    }

    /**
     * 设置用户配置。
     * @param userId 用户ID
     * @param key    配置键
     * @param value  配置值
     */
    public void setUserConfig(String userId, String key, Object value) {
        set(Scope.USER, userId, key, value);
    }

    /**
     * 设置全局配置。
     * @param key   配置键
     * @param value 配置值
     */
    public void setGlobalConfig(String key, Object value) {
        set(Scope.GLOBAL, GLOBAL_SCOPE_ID, key, value);
    }


    // =================================================================
    // REMOVERS (新增的删除方法)
    // =================================================================

    /**
     * 删除一个指定的配置项。
     * 注意：删除后，该项的配置会回退到下一级（例如，删除群配置后，会采用全局配置）。
     *
     * @param scope   配置范围
     * @param scopeId 范围ID
     * @param key     配置键
     * @return 如果成功删除则返回 true，否则 false
     */
    @Transactional
    public boolean remove(String scope, String scopeId, String key) {
        return configService.remove(
                new QueryWrapper<WinefoxBotAppConfig>()
                        .eq("scope", scope)
                        .eq("scope_id", scopeId)
                        .eq("config_key", key)
        );
    }

    /**
     * 删除一个群组的特定配置。
     */
    public boolean removeGroupConfig(String groupId, String key) {
        return remove("group", groupId, key);
    }

    /**
     * 删除一个用户的特定配置。
     */
    public boolean removeUserConfig(String userId, String key) {
        return remove("user", userId, key);
    }

    private String convertScopeEnumToStringValue(Scope scope) {
        return switch (scope) {
            case GLOBAL -> "global";
            case GROUP -> "group";
            case USER -> "user";
        };
    }
}
