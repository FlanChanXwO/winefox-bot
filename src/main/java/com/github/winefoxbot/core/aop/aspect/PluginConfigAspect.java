package com.github.winefoxbot.core.aop.aspect;

import com.github.winefoxbot.core.annotation.plugin.Plugin;
import com.github.winefoxbot.core.config.plugin.BasePluginConfig;
import com.github.winefoxbot.core.context.BotContext;
import com.github.winefoxbot.core.service.plugin.PluginService;
import com.github.winefoxbot.core.utils.PluginConfigBinder;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import com.mikuac.shiro.dto.event.message.MessageEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.aop.support.AopUtils; // 引入 AopUtils
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 插件配置切面
 * @author FlanChan
 */
@Aspect
@Component
@Order(2)
@Slf4j
@RequiredArgsConstructor
public class PluginConfigAspect {

    private final PluginConfigBinder configBinder;
    private final PluginService pluginService;

    @Around("@within(pluginAnno) && " +
            "(@annotation(com.mikuac.shiro.annotation.AnyMessageHandler) || " +
            "@annotation(com.mikuac.shiro.annotation.GroupMessageHandler) || " +
            "@annotation(com.mikuac.shiro.annotation.PrivateMessageHandler))")
    public Object handlePluginConfig(ProceedingJoinPoint joinPoint, Plugin pluginAnno) throws Throwable {
        // 1. 检查外层是否已经准备好环境
        if (!BotContext.CURRENT_MESSAGE_EVENT.isBound()) {
            return joinPoint.proceed();
        }

        // 获取当前被拦截的插件 Bean 的真实类型
        Class<?> targetClass = AopUtils.getTargetClass(joinPoint.getTarget());
        // 获取插件 ID (即类名，与 PluginConfigService 中保持一致)
        String pluginId = targetClass.getSimpleName();

        // 优先检查全局开关 (对应数据库中的 system.plugin.status.XXX)
        // 如果这里返回 false，直接拦截，不再进行后续的 Config 实例化
        if (!pluginService.getPluginEnabledStatus(pluginId)) {
            // log.debug 避免日志刷屏，但在调试时很有用
            log.debug("插件 [{}] (ID: {}) 全局开关已关闭，拦截执行", pluginAnno.name(), pluginId);
            return null;
        }

        // 2. 准备配置逻辑
        Class<? extends BasePluginConfig> configClass = pluginAnno.config();
        BasePluginConfig configInstance;

        if (configClass == BasePluginConfig.None.class) {
            configInstance = new BasePluginConfig.None();
        } else {
            MessageEvent event = BotContext.CURRENT_MESSAGE_EVENT.get();
            Long groupId = null;
            Long userId = event.getUserId();
            if (event instanceof GroupMessageEvent groupEvent) {
                groupId = groupEvent.getGroupId();
            }

            // 3. 实例化并绑定配置
            configInstance = configClass.getDeclaredConstructor().newInstance();
            configBinder.bind(configInstance, groupId, userId);
        }

        // 3. 执行业务
        return BotContext.callWithConfig(configInstance, () -> {
            try {
                return joinPoint.proceed();
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        });
    }
}
