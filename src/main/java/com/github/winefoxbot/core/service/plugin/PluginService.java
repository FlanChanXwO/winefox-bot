package com.github.winefoxbot.core.service.plugin;

import cn.hutool.core.convert.Convert;
import cn.hutool.json.JSONUtil;
import com.github.winefoxbot.core.annotation.plugin.ConfigItem;
import com.github.winefoxbot.core.annotation.plugin.Plugin;
import com.github.winefoxbot.core.annotation.plugin.PluginConfig;
import com.github.winefoxbot.core.config.plugin.BasePluginConfig;
import com.github.winefoxbot.core.manager.ConfigManager;
import com.github.winefoxbot.core.model.entity.WinefoxBotPluginConfig;
import com.github.winefoxbot.core.model.type.BaseEnum;
import com.github.winefoxbot.core.model.vo.webui.resp.PluginConfigSchemaResponse;
import com.github.winefoxbot.core.model.vo.webui.resp.PluginListItemResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class PluginService {

    private final ApplicationContext applicationContext;
    private final ConfigManager configManager;
    private final static String PLUGIN_STATUS_PREFIX = "plugin.status.";

    /**
     * 获取插件列表 (保持不变)
     */
    public List<PluginListItemResponse> getPluginList(String keyword) {
        Map<String, Object> pluginBeans = applicationContext.getBeansWithAnnotation(Plugin.class);
        List<PluginListItemResponse> list = new ArrayList<>();

        for (Object bean : pluginBeans.values()) {
            Class<?> clazz = AopUtils.getTargetClass(bean);
            Plugin pluginAnn = clazz.getAnnotation(Plugin.class);

            if (pluginAnn == null || pluginAnn.hidden()) continue;

            if (keyword != null && !keyword.isBlank()) {
                if (!pluginAnn.name().contains(keyword) && !pluginAnn.description().contains(keyword)) {
                    continue;
                }
            }

            boolean hasConfig = pluginAnn.config() != null && pluginAnn.config() != BasePluginConfig.None.class;
            boolean isEnabled = isPluginEnabled(clazz.getSimpleName());

            list.add(PluginListItemResponse.builder()
                    .id(clazz.getSimpleName())
                    .name(pluginAnn.name())
                    .canDisable(pluginAnn.canDisable())
                    .description(pluginAnn.description())
                    .version(pluginAnn.version())
                    .author(pluginAnn.author())
                    .iconPath(pluginAnn.iconPath())
                    .builtIn(pluginAnn.builtIn())
                    .hasConfig(hasConfig)
                    .enabled(isEnabled)
                    .build());
        }
        return list;
    }

    /**
     * 切换插件开启/关闭 (保持不变)
     */
    public void togglePlugin(String pluginId, boolean enable) {
        configManager.set(
                ConfigManager.Scope.GLOBAL,
                "default",
                PLUGIN_STATUS_PREFIX + pluginId,
                enable,
                "插件启用状态: " + pluginId,
                "system_internal"
        );
        log.info("插件 {} 状态更新为: {}", pluginId, enable);
    }

    public boolean getPluginEnabledStatus(String pluginId) {
        return isPluginEnabled(pluginId);
    }

    /**
     * 获取配置表单 Schema 和 当前全局值
     */
    public PluginConfigSchemaResponse getPluginConfigSchema(String pluginId) {
        Object pluginBean = findPluginBeanBySimpleName(pluginId);
        if (pluginBean == null) throw new IllegalArgumentException("插件不存在");

        Class<?> pluginClass = AopUtils.getTargetClass(pluginBean);
        Plugin pluginAnn = pluginClass.getAnnotation(Plugin.class);
        Class<? extends BasePluginConfig> configClass = pluginAnn.config();

        List<PluginConfigSchemaResponse.ConfigField> fields = new ArrayList<>();

        PluginConfig configAnn = null;
        PluginConfigSchemaResponse.PluginConfigSchemaResponseBuilder builder = PluginConfigSchemaResponse.builder();
        if (configClass != BasePluginConfig.None.class) {
            configAnn = configClass.getAnnotation(PluginConfig.class);
            builder.allowedScopes(
                    Arrays.stream(configAnn.scopes())
                            .map(Enum::name)
                            .toList()
            );
            String prefix = configAnn.prefix();

            for (Field field : configClass.getDeclaredFields()) {
                if (!field.isAnnotationPresent(ConfigItem.class)) continue;

                ConfigItem item = field.getAnnotation(ConfigItem.class);
                String fullKey = prefix + "." + item.key();
                Class<?> fieldType = field.getType();

                // 初始化 Builder
                var dtoBuilder = PluginConfigSchemaResponse.ConfigField.builder()
                        .key(fullKey)
                        .label(item.description().split("。")[0]) // 简单截取第一句做 label
                        .description(item.description())
                        .defaultValue(item.defaultValue()); // 默认值暂存字符串

                // === 核心逻辑修改开始 ===

                // 1. 处理 List/Set 集合类型
                if (Collection.class.isAssignableFrom(fieldType)) {
                    dtoBuilder.type("array");

                    // 推断集合元素的泛型类型 (itemType)
                    String itemType = "string"; // 默认
                    Type genericType = field.getGenericType();
                    if (genericType instanceof ParameterizedType pt) {
                        Type actualType = pt.getActualTypeArguments()[0];
                        // 简单判断泛型类型
                        if (actualType.getTypeName().contains("Integer") || actualType.getTypeName().contains("Long")) {
                            itemType = "number";
                        }
                    }
                    dtoBuilder.itemType(itemType); // 设置 itemType 给前端

                    // 获取当前值：ConfigManager 存的是字符串 (JSON数组或逗号分隔)
                    // 使用 Hutool 将其转回 List 给前端
                    String rawStr = configManager.get(fullKey, null, null, String.class)
                            .orElse(item.defaultValue());

                    // 如果是 JSON 数组字符串，转为 List；如果是空，转为空 List
                    try {
                        // 尝试作为 List 解析，这里假设 rawStr 是 JSON 格式或者逗号分隔
                        // 如果 rawStr 是 "sfw", 它会变成 ["sfw"]
                        dtoBuilder.value(Convert.toList(Object.class, rawStr));
                    } catch (Exception e) {
                        log.warn("解析列表配置失败: {}", fullKey);
                        dtoBuilder.value(Collections.emptyList());
                    }
                }

                // 2. 处理枚举 (Select)
                else if (fieldType.isEnum()) {
                    dtoBuilder.type("select");

                    List<Map<String, String>> options = new ArrayList<>();
                    for (Object constant : fieldType.getEnumConstants()) {
                        if (constant instanceof BaseEnum<?> baseEnum) {
                            Map<String, String> option = new HashMap<>();
                            option.put("value", String.valueOf(baseEnum.getValue()));

                            // 获取 Label (description 字段)
                            String label = String.valueOf(baseEnum.getValue());
                            try {
                                Field descField = fieldType.getDeclaredField("description");
                                descField.setAccessible(true);
                                Object descVal = descField.get(constant);
                                if (descVal != null) label = descVal.toString();
                            } catch (Exception ignored) {
                            }

                            option.put("label", label);
                            options.add(option);
                        }
                    }
                    dtoBuilder.options(options);

                    // 设置当前值 (String)
                    dtoBuilder.value(configManager.get(fullKey, null, null, String.class)
                            .orElse(item.defaultValue()));
                }

                // 3. 处理 Map (键值对) 类型
                else if (Map.class.isAssignableFrom(fieldType)) {
                    dtoBuilder.type("map");

                    // --- Step A: 推断 Map Value 的类型 (用来设置 itemType) ---
                    String mapValueType = "string"; // 默认为 string
                    Type genericType = field.getGenericType();

                    if (genericType instanceof ParameterizedType pt) {
                        Type[] typeArgs = pt.getActualTypeArguments();
                        // Map<K, V> 有两个泛型参数，我们关注 V (index 1)
                        if (typeArgs.length == 2) {
                            Type valueType = typeArgs[1];
                            if (valueType.getTypeName().contains("Integer") ||
                                    valueType.getTypeName().contains("Long") ||
                                    valueType.getTypeName().contains("Double")) {
                                mapValueType = "number";
                            } else if (valueType.getTypeName().contains("Boolean")) {
                                mapValueType = "bool";
                            }
                        }
                    }
                    dtoBuilder.itemType(mapValueType);

                    // --- Step B: 获取并解析当前值 ---

                    // 1. 获取 rawStr: 从 ConfigManager 获取，如果没有就用 ConfigItem 的默认值
                    // 注意：默认值 defaultValue() 可能是 null，或者是一个 JSON 字符串 "{}"
                    String rawStr = configManager.get(fullKey, null, null, String.class)
                            .orElse(item.defaultValue()); // 如果 ConfigManager 没值，取注解上的默认值

                    // 2. 防御性处理: 如果 rawStr 还是 null 或者空字符串，给一个合法的空 JSON
                    if (rawStr == null || rawStr.isBlank()) {
                        rawStr = "{}";
                    }

                    try {
                        // 3. 转换: JSON String -> Map
                        // 使用 Hutool 的 JSONUtil.toBean 还是 JSONUtil.parseObj ?
                        // 建议直接用 parseObj 转为 JSONObject (实现了 Map 接口)，或者转为具体的 Map
                        // 这里的 value 最终会序列化给前端，所以 Map<String, Object> 最合适
                        dtoBuilder.value(JSONUtil.parseObj(rawStr));
                    } catch (Exception e) {
                        log.warn("配置项 {} 解析 Map 失败, rawValue: {}", fullKey, rawStr);
                        // 解析失败时，返回空 Map，防止前端崩溃
                        dtoBuilder.value(Map.of());
                    }
                }

                // 4. 基础类型
                else {
                    dtoBuilder.type(determineFieldType(fieldType));
                    // 设置当前值
                    String rawStr = configManager.get(fullKey, null, null, String.class)
                            .orElse(item.defaultValue());

                    // 最好根据类型转回对应的 JSON 原始类型 (bool/number) 否则前端可能收到 "true" 字符串而不是 true 布尔值
                    dtoBuilder.value(Convert.convert(fieldType, rawStr));
                }

                // === 核心逻辑修改结束 ===

                fields.add(dtoBuilder.build());
            }
        }

        return builder
                .pluginName(pluginAnn.name())
                .description(pluginAnn.description())
                .fields(fields)
                .build();
    }

    /**
     * 5. 获取指定作用域的配置列表
     * 用于“配置管理”面板，查看当前有哪些覆盖配置
     */
    public List<Map<String, Object>> getPluginConfigList(ConfigManager.Scope scope, String scopeId) {
        List<WinefoxBotPluginConfig> configs = configManager.list(scope, scopeId);

        // 转换为前端友好的格式
        return configs.stream().map(entity -> {
            Map<String, Object> map = new HashMap<>();
            map.put("key", entity.getConfigKey());
            map.put("value", entity.getConfigValue());
            map.put("description", entity.getDescription());
            map.put("group", entity.getConfigGroup());

            map.put("scope", entity.getScope());
            map.put("scopeId", entity.getScopeId());

            map.put("updatedAt", entity.getUpdatedAt());
            return map;
        }).toList();
    }

    /**
     * 6. 删除指定配置
     */
    public void deleteConfig(String key, String scopeStr, String scopeId) {
        // 解析 Scope
        ConfigManager.Scope scope;
        try {
            scope = ConfigManager.Scope.valueOf(scopeStr.toUpperCase());
        } catch (Exception e) {
            scope = ConfigManager.Scope.GLOBAL;
        }

        // 只有非 Global 或者 Global 确实允许删除时才操作
        // 通常 Global 是代码定义的默认值，删了也会被初始化回来，或者作为硬删除
        // 这里直接调用 Manager 删除
        boolean removed = configManager.remove(
                scope == ConfigManager.Scope.GLOBAL ? "global" :
                        scope == ConfigManager.Scope.GROUP ? "group" : "user",
                scopeId,
                key
        );

        if (removed) {
            log.info("已删除配置: key={}, scope={}, scopeId={}", key, scope, scopeId);
        } else {
            log.warn("尝试删除不存在的配置: key={}, scope={}, scopeId={}", key, scope, scopeId);
        }
    }


    /**
     * 7. (新增) 删除指定插件在指定作用域下的所有配置 (整组删除)
     */
    public void deleteConfigByScope(String pluginId, String scopeStr, String scopeId) {
        // 1. 获取插件配置前缀
        Object pluginBean = findPluginBeanBySimpleName(pluginId);
        if (pluginBean == null) return;

        Class<?> pluginClass = AopUtils.getTargetClass(pluginBean);
        Plugin pluginAnn = pluginClass.getAnnotation(Plugin.class);
        if (pluginAnn.config() == BasePluginConfig.None.class) return;

        PluginConfig configAnn = pluginAnn.config().getAnnotation(PluginConfig.class);
        String prefix = configAnn.prefix(); // 例如 "setu"

        // 2. 解析 Scope
        ConfigManager.Scope scope;
        try {
            scope = ConfigManager.Scope.valueOf(scopeStr.toUpperCase());
        } catch (Exception e) {
            return;
        }

        // 3. 查出该作用域下所有配置，筛选出符合前缀的 keys
        List<WinefoxBotPluginConfig> allConfigs = configManager.list(scope, scopeId);

        for (WinefoxBotPluginConfig config : allConfigs) {
            if (config.getConfigKey().startsWith(prefix + ".")) {
                // 逐个删除 (或者 ConfigManager 可以提供 deleteByWrapper)
                configManager.remove(scopeStr.toLowerCase(), scopeId, config.getConfigKey());
            }
        }
        log.info("已重置插件 {} 在 {} - {} 下的所有配置", pluginId, scopeStr, scopeId);
    }

    // --- 辅助方法 ---

    private boolean isPluginEnabled(String pluginId) {
        return configManager.get(PLUGIN_STATUS_PREFIX + pluginId , Boolean.class)
                .orElse(true);
    }

    private Object findPluginBeanBySimpleName(String simpleName) {
        Map<String, Object> beans = applicationContext.getBeansWithAnnotation(Plugin.class);
        for (Object bean : beans.values()) {
            if (AopUtils.getTargetClass(bean).getSimpleName().equals(simpleName)) {
                return bean;
            }
        }
        return null;
    }

    private String determineFieldType(Class<?> type) {
        if (type == Boolean.class || type == boolean.class) return "bool";
        if (Number.class.isAssignableFrom(type) || type == int.class || type == long.class) return "number";
        return "string";
    }
}
