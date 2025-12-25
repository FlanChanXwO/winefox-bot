package com.github.winefoxbot.aop;

import com.github.winefoxbot.service.shiro.ShiroSessionStateService;
import com.mikuac.shiro.dto.event.Event;
import com.mikuac.shiro.dto.event.message.MessageEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
@Order(1)
public class ShiroSessionContextAspect {

    private final ShiroSessionStateService sessionStateService;

    /**
     * 定义一个环绕通知，拦截所有使用 @Block 注解的方法。
     *
     * @param joinPoint 连接点，代表被拦截的方法
     * @return 方法的执行结果，如果被拦截则返回 null
     * @throws Throwable 如果被拦截方法抛出异常
     */
    @Around("@annotation(com.github.winefoxbot.annotation.Block)")
    public Object checkSessionContext(ProceedingJoinPoint joinPoint) throws Throwable {
        Object[] args = joinPoint.getArgs();
        Event event = null;

        // 1. 从方法参数中找到 AnyMessageEvent 实例
        for (Object arg : args) {
            if (arg instanceof Event) {
                event = (MessageEvent) arg;
                break;
            }
        }

        if (event == null) {
            // 如果方法签名中没有事件参数，则无法进行检查，直接放行
            log.warn("方法 {} 使用了 @Block 注解，但未找到 MessageEvent 类型的参数，无法进行上下文检查。", joinPoint.getSignature().toShortString());
            return joinPoint.proceed();
        }

        // 2. 获取会话 Key 和当前状态
        String sessionKey = sessionStateService.getSessionKey(event);
        String state = sessionStateService.getSessionState(sessionKey);

        // 3. 如果用户处于某个特定状态，则中断方法执行
        if (StringUtils.hasText(state)) {
            log.debug("用户 {} 处于会话状态 [{}]，已拦截方法 {}", sessionKey, state, joinPoint.getSignature().toShortString());
            return null; // 中断执行
        }

        // 4. 如果用户不在任何特殊状态，则正常执行原方法
        return joinPoint.proceed();
    }
}