package com.github.winefoxbot.core.utils;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;
import com.github.winefoxbot.core.annotation.plugin.ConfigItem;
import com.github.winefoxbot.core.annotation.plugin.PluginConfig;
import com.github.winefoxbot.core.manager.ConfigManager;
import com.github.winefoxbot.core.model.type.BaseEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * 插件配置绑定器
 * 负责将 ConfigManager 中的扁平化 Key 绑定到复杂的嵌套 Java 对象上
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PluginConfigBinder {

    private final ConfigManager configManager;

    /**
     * 将配置绑定到对象实例
     *
     * @param configObj 配置对象实例
     * @param groupId   群组ID (用于获取群特定配置)
     * @param userId    用户ID (用于获取用户特定配置)
     */
    public void bind(Object configObj, Long groupId, Long userId) {
        if (configObj == null) return;

        Class<?> clazz = configObj.getClass();

        // 1. 获取根前缀
        String rootPrefix = "";
        if (clazz.isAnnotationPresent(PluginConfig.class)) {
            rootPrefix = clazz.getAnnotation(PluginConfig.class).prefix();
        }

        // 2. 递归绑定
        recursiveBind(configObj, clazz, rootPrefix, groupId, userId);
    }

    /**
     * 递归绑定配置
     */
    private void recursiveBind(Object currentObj, Class<?> currentClass, String currentPrefix, Long groupId, Long userId) {
        if (currentObj == null || currentClass == Object.class) return;

        // 1. 先递归处理父类字段
        recursiveBind(currentObj, currentClass.getSuperclass(), currentPrefix, groupId, userId);

        Field[] fields = currentClass.getDeclaredFields();

        for (Field field : fields) {
            // 设置可访问，Hutool ReflectUtil 也可以做，这里保留原生
            field.setAccessible(true);

            // --- 情况 A: 叶子节点 (@ConfigItem) ---
            if (field.isAnnotationPresent(ConfigItem.class)) {
                ConfigItem annotation = field.getAnnotation(ConfigItem.class);
                String itemKey = annotation.key();
                String fullKey = StrUtil.isBlank(currentPrefix) ? itemKey : currentPrefix + "." + itemKey;

                try {
                    // 1. 尝试从 ConfigManager 获取值
                    // 策略：统一以 String 类型取出，然后由 Binder 进行类型转换
                    // 这样做的好处是解耦 ConfigManager 的存储格式和 Java 对象的实际类型
                    Optional<String> valueOpt = configManager.get(fullKey, userId, groupId, String.class);

                    String rawValue;
                    if (valueOpt.isPresent()) {
                        rawValue = valueOpt.get();
                    } else {
                        // 2. 如果数据库无值，且当前字段为null（未初始化），则使用注解默认值
                        // 如果字段已有值（比如构造函数里赋了初值），则不覆盖
                        if (field.get(currentObj) == null) {
                            rawValue = annotation.defaultValue();
                        } else {
                            rawValue = null; // 跳过赋值，保持原样
                        }
                    }

                    // 3. 执行转换并赋值
                    if (rawValue != null) {
                        setFieldValue(currentObj, field, rawValue);
                    }

                } catch (Exception e) {
                    log.error("绑定配置失败 Key: {}", fullKey, e);
                }
            }
            // --- 情况 B: 嵌套对象 ---
            else if (isNestedObject(field.getType())) {
                try {
                    Object nestedObj = field.get(currentObj);
                    // 如果嵌套对象为 null，先实例化它
                    if (nestedObj == null) {
                        nestedObj = ReflectUtil.newInstance(field.getType());
                        field.set(currentObj, nestedObj);
                    }

                    // 确定下一层的前缀
                    String nextPrefix = StrUtil.isBlank(currentPrefix) ? field.getName() : currentPrefix + "." + field.getName();

                    recursiveBind(nestedObj, field.getType(), nextPrefix, groupId, userId);
                } catch (Exception e) {
                    log.error("初始化嵌套配置对象失败: {}", field.getName(), e);
                }
            }
        }
    }

    /**
     * 将字符串值转换为目标字段类型并赋值 (核心转换逻辑)
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void setFieldValue(Object target, Field field, String valueStr) {
        Class<?> type = field.getType();
        Object convertedValue = null;

        try {
            // 1. 特殊处理 BaseEnum (项目自定义枚举接口)
            if (type.isEnum() && BaseEnum.class.isAssignableFrom(type)) {
                boolean found = false;
                for (Object constant : type.getEnumConstants()) {
                    BaseEnum baseEnum = (BaseEnum) constant;
                    // 对比 value (例如 "sfw" == "sfw")
                    if (String.valueOf(baseEnum.getValue()).equalsIgnoreCase(valueStr)) {
                        convertedValue = constant;
                        found = true;
                        break;
                    }
                }
                // 如果没找到匹配的 Enum 值，尝试使用默认值或报错，这里选择使用第一个作为兜底
                if (!found) {
                    log.warn("无法将值 '{}' 转换为枚举 {}, 将使用默认值。", valueStr, type.getSimpleName());
                    if (type.getEnumConstants().length > 0) {
                        convertedValue = type.getEnumConstants()[0];
                    }
                }
            }

            // 2. 处理 List / Set / Array 集合类型 【重点】
            // Hutool 的 Convert.convert 会自动处理逗号分隔字符串，例如 "a,b,c" -> List<String>
            // 也可以处理 JSON 字符串，例如 "['a','b']" -> List<String>
            else if (Collection.class.isAssignableFrom(type) || type.isArray()) {
                // 这里有一个细节：我们需要告诉 Convert 集合里的元素类型是什么
                // 使用 Hutool 的 Convert.convert(Type type, Object value) 方法
                // field.getGenericType() 可以拿到 List<String> 这种完整类型信息
                convertedValue = Convert.convert(field.getGenericType(), valueStr);
            }

            else if (Map.class.isAssignableFrom(type)) {
                // 处理 Map 类型
                // 假设 valueStr 是 JSON 格式: {"key":"val"}
                // Convert 能够识别 JSON 并转换为 Map
                convertedValue = Convert.convert(field.getGenericType(), valueStr);
            }

            // 3. 其他所有类型 (Int, Boolean, String, Date, Standard Enum) 交给 Hutool
            else {
                convertedValue = Convert.convert(type, valueStr);
            }

            // 4. 赋值
            if (convertedValue != null) {
                field.set(target, convertedValue);
            }

        } catch (Exception e) {
            log.error("类型转换赋值异常 Field: {}, Value: {}", field.getName(), valueStr, e);
        }
    }

    /**
     * 判断是否为嵌套对象 (非基本类型、非String、非集合、非Map、非Enum)
     */
    private boolean isNestedObject(Class<?> type) {
        return !type.isPrimitive() &&
                !StrUtil.startWith(type.getName(), "java.lang") && // 使用 Hutool 判断前缀
                !type.isEnum() &&
                !Collection.class.isAssignableFrom(type) &&
                !Map.class.isAssignableFrom(type);
    }
}
