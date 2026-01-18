package com.github.winefoxbot.core.service.webui;

import com.github.winefoxbot.core.annotation.webui.EnableConfigDashboard;
import com.github.winefoxbot.core.annotation.webui.ShowInDashboard;
import com.github.winefoxbot.core.model.vo.webui.resp.ConfigItemResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.util.ReflectionUtils; // Spring自带反射工具，很好用
import org.springframework.util.ClassUtils;
import java.util.*;

/**
 * @author FlanChan
 */
@Service
@RequiredArgsConstructor
public class WebUIConfigDashboardService {

    private final ApplicationContext applicationContext;

    public List<ConfigItemResponse> getAllConfigs() {
        List<ConfigItemResponse> result = new ArrayList<>();

        // 1. 关键步骤：查找所有标记了 @EnableConfigDashboard 的 Bean
        Map<String, Object> beans = applicationContext.getBeansWithAnnotation(EnableConfigDashboard.class);

        // 2. 遍历每一个 Bean
        for (Object bean : beans.values()) {
            Class<?> targetClass = ClassUtils.getUserClass(bean);

            // 处理 CGLIB 代理情况 (如果类被AOP代理，需要获取原始类才能读到字段注解)
            // 虽然配置类一般很少被代理，但为了健壮性加上这句
            // Class<?> userClass = ClassUtils.getUserClass(targetClass);

            // 3. 遍历 Bean 的所有字段
            ReflectionUtils.doWithFields(targetClass, field -> {
                // 4. 检查字段是否有 @ShowInDashboard
                if (field.isAnnotationPresent(ShowInDashboard.class)) {
                    ShowInDashboard annotation = field.getAnnotation(ShowInDashboard.class);
                    ReflectionUtils.makeAccessible(field); // 强行访问 private

                    Object value = field.get(bean);

                    result.add(new ConfigItemResponse(
                            annotation.label(),
                            value != null ? value.toString() : "N/A",
                            annotation.description(),
                            annotation.order()
                    ));
                }
            });
        }

        // 5. 全局排序
        result.sort(Comparator.comparingInt(ConfigItemResponse::order));
        return result;
    }

}
