// file: com/github/winefoxbotbackend/utils/SpringBeanUtil.java
package com.github.winefoxbot.utils;

import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

public final class SpringBeanUtil {

    /**
     * 根据Class类型从Spring上下文中获取其Bean名称。
     * @param context Spring的ApplicationContext
     * @param beanClass Bean的Class对象
     * @return Bean的名称
     * @throws NoSuchBeanDefinitionException 如果找不到该类型的Bean
     */
    public static String getBeanName(ApplicationContext context, Class<?> beanClass) {
        // 优先尝试通过类型直接获取，这是最准确的方式
        String[] beanNames = context.getBeanNamesForType(beanClass);
        if (beanNames.length == 1) {
            return beanNames[0];
        }

        // 如果有多个同类型的Bean，或一个都没有，则尝试通过注解和默认规则推断
        if (beanNames.length > 1) {
            throw new IllegalStateException("找到了多个类型为 " + beanClass.getSimpleName() + " 的Bean，无法确定使用哪一个。");
        }
        
        // 如果context中找不到，尝试从注解中解析
        Component componentAnnotation = beanClass.getAnnotation(Component.class);
        if (componentAnnotation != null && StringUtils.hasText(componentAnnotation.value())) {
            return componentAnnotation.value();
        }

        // 使用Spring的默认命名规则：类名首字母小写
        String simpleName = beanClass.getSimpleName();
        return StringUtils.uncapitalize(simpleName);
    }
}
