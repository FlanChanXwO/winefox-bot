package com.github.winefoxbot.plugins;

import com.mikuac.shiro.annotation.MessageHandlerFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Map;

@Slf4j
public abstract class BasePlugin {

    private static final String[] COMMAND_START = {"/", "", "$"};
    private static final String[] COMMAND_SEP = {".", " "};

    public BasePlugin() {
        // 关键改动：遍历当前实例（子类）的所有方法
        System.out.println(this.getClass().getName());
        ReflectionUtils.doWithMethods(this.getClass(), method -> {
            // 在方法上查找注解
            MessageHandlerFilter filterAnnotation = method.getAnnotation(MessageHandlerFilter.class);
            if (filterAnnotation != null) {
            System.out.println("Found method: " + method.getName() + " with MessageHandlerFilter annotation.");
                try {
                    modifyAnnotationValue(filterAnnotation, "startWith", COMMAND_START);
                    System.out.println(Arrays.toString(filterAnnotation.startWith()));
                } catch (NoSuchFieldException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    /**
     * 动态修改注解属性的核心逻辑 (保持不变)
     */
    private void modifyAnnotationValue(Object annotation, String attributeName, Object newValue) throws NoSuchFieldException, IllegalAccessException {
        InvocationHandler handler = Proxy.getInvocationHandler(annotation);
        Field memberValuesField = handler.getClass().getDeclaredField("memberValues");
        memberValuesField.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> memberValues = (Map<String, Object>) memberValuesField.get(handler);

        memberValues.put(attributeName, newValue);
    }
}
