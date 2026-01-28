package com.github.winefoxbot.core.aop.aspect;

import com.github.winefoxbot.core.context.BotContext;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.AnyMessageEvent;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import com.mikuac.shiro.dto.event.message.MessageEvent;
import com.mikuac.shiro.dto.event.notice.PokeNoticeEvent;
import jodd.bean.BeanUtil;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.beans.BeanUtils;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Bot上下文切面.
 * <p>
 * 用于拦截所有Shiro插件的消息处理方法，自动提取Bot和Event参数，
 * 并将其绑定到 ScopedValue 上下文中，供后续业务逻辑或异常处理器使用。
 *
 * @author FlanChan (badapple495@outlook.com)
 * @since 2026-01-19-15:34
 */
@Aspect
@Component
@Order(0) // 保证优先级最高，最先执行，包裹在最外层
@Slf4j
public class BotContextAspect {

    /**
     * 定义切入点 (Pointcut)
     * <p>
     * 1. 类级别约束:
     * - @within(com.mikuac.shiro.annotation.common.Shiro): 类上必须有 @Shiro 注解
     * - @within(org.springframework.stereotype.Component): 类必须被 Spring 管理
     * <p>
     * 2. 方法级别约束:
     * - 仅针对三个核心消息处理注解
     */
    @Pointcut("(" +
            // 场景A: 使用了 @Plugin 自定义注解 (内部含有 @Shiro 和 @Component)
            "@within(com.github.winefoxbot.core.annotation.plugin.Plugin) || " +
            // 场景B: 原生 Shiro 写法 (直接标记 @Shiro 和 @Component)
            "(@within(com.mikuac.shiro.annotation.common.Shiro) && @within(org.springframework.stereotype.Component))" +
            ") && " +
            // 方法级别约束
            "(@annotation(com.mikuac.shiro.annotation.PrivatePokeNoticeHandler) || " +
            "@annotation(com.mikuac.shiro.annotation.GroupPokeNoticeHandler) || " +
            "@annotation(com.mikuac.shiro.annotation.AnyMessageHandler) || " +
            "@annotation(com.mikuac.shiro.annotation.GroupMessageHandler) || " +
            "@annotation(com.mikuac.shiro.annotation.PrivateMessageHandler))")
    public void botContextPointcut() {
    }

    @Around("botContextPointcut()")
    public Object setBotContext(ProceedingJoinPoint joinPoint) throws Throwable {
        // 1. 尝试从参数中获取 Bot 和 MessageEvent
        Object[] args = joinPoint.getArgs();
        Bot bot = null;
        MessageEvent event = null;

        for (Object arg : args) {
            if (arg instanceof Bot b) {
                bot = b;
            } else if (arg instanceof MessageEvent e) {
                event = e;
            } else if (arg instanceof PokeNoticeEvent e) {
                event = new AnyMessageEvent();
                BeanUtils.copyProperties(e, event);
                event.setMessageType(e.getGroupId() == null ? "private" : "group");
            }
        }

        // 2. 如果参数齐全，则绑定上下文并执行
        if (bot != null && event != null) {
            // 使用 BotContext 的 ScopedValue 包装执行范围
            return BotContext.callWithContext(bot, event, () -> {
                try {
                    return joinPoint.proceed();
                } catch (Throwable e) {
                    // Callable 只能抛出 Exception，如果 proceed 抛出了 Error (如 OutOfMemoryError)，需要特殊处理
                    if (e instanceof Exception ex) {
                        throw ex;
                    } else if (e instanceof Error er) {
                        throw er;
                    } else {
                        throw new IllegalStateException("Unexpected Throwable", e);
                    }
                }
            });
        }

        // 3. 如果未能解析出上下文，直接执行原方法（相当于不启用上下文功能）
        return joinPoint.proceed();
    }
}
