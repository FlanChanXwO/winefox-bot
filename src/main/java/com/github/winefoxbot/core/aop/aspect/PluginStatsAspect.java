package com.github.winefoxbot.core.aop.aspect;

import com.github.winefoxbot.core.annotation.plugin.Plugin;
import com.github.winefoxbot.core.event.PluginCalledEvent;
import com.mikuac.shiro.annotation.MessageHandlerFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;


/**
 * @author FlanChan
 */
@Aspect
@Component
@Slf4j
@Order(100)
@RequiredArgsConstructor
public class PluginStatsAspect {

    private final ApplicationEventPublisher eventPublisher;

    // 拦截带有 @Plugin 注解的类中的方法
    @Around("@within(pluginAnno)")
    public Object collectStats(ProceedingJoinPoint joinPoint, Plugin pluginAnno) throws Throwable {
        // 1. 获取方法上的 MessageHandlerFilter 注解
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        MessageHandlerFilter filter = method.getAnnotation(MessageHandlerFilter.class);

        // 2. 过滤无意义的插件调用统计
        boolean shouldRecord = true;
        if (filter != null) {
            String cmd = filter.cmd();
            if ((cmd == null || cmd.isEmpty())) {
                shouldRecord = false;
            }
        } else {
            shouldRecord = false;
        }

        if (shouldRecord) {
            try {
                String className = joinPoint.getTarget().getClass().getName();
                eventPublisher.publishEvent(new PluginCalledEvent(joinPoint.getThis(), className, pluginAnno));
            } catch (Exception e) {
                log.warn("插件统计埋点异常", e);
            }
        }

        // 继续执行原方法
        return joinPoint.proceed();
    }
}
