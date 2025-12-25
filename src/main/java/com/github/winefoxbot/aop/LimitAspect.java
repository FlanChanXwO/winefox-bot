package com.github.winefoxbot.aop;

import com.github.winefoxbot.annotation.Limit;
import com.github.winefoxbot.exception.RateLimitException;
import com.github.winefoxbot.exception.handler.GlobalBotRateLimitExceptionHandler;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.AnyMessageEvent;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import com.mikuac.shiro.dto.event.message.MessageEvent;
import com.mikuac.shiro.dto.event.message.PrivateMessageEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
@Order(11)
public class LimitAspect {

    // Value 从 Object[] 变为更清晰的 LimitState 对象
    private final Map<String, LimitState> limiterCache = new ConcurrentHashMap<>();
    private final GlobalBotRateLimitExceptionHandler exceptionHandler;

    @Before("@within(com.github.winefoxbot.annotation.Limit) || @annotation(com.github.winefoxbot.annotation.Limit)")
    public void before(JoinPoint joinPoint) throws Throwable {
        Object[] args = joinPoint.getArgs();
        Bot bot = findBot(args);
        MessageEvent event = findMessageEvent(args);

        if (bot == null || event == null) return;

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Limit limit = findLimitAnnotation(method, joinPoint.getTarget().getClass());
        if (limit == null) return;

        String methodKey = signature.getDeclaringTypeName() + "." + method.getName();

        try {
            if (limit.globalPermits() > 0) {
                checkLimit("global:" + methodKey, limit, bot, event);
            }
            if (limit.userPermits() > 0) {
                String sessionKey = getSessionIdentifier(event, methodKey);
                checkLimit(sessionKey, limit, bot, event);
            }
        } catch (RateLimitException e) {
            exceptionHandler.handleRateLimitException(e);
            throw new InterruptedException("Rate limit triggered by AOP");
        }
    }

    private void checkLimit(String key, Limit limit, Bot bot, MessageEvent event) throws RateLimitException, InterruptedException {
        int permits = key.startsWith("global:") ? limit.globalPermits() : limit.userPermits();
        int timeInSeconds = limit.timeInSeconds();
        int notificationIntervalSeconds = limit.notificationIntervalSeconds();

        // 获取或创建状态对象
        LimitState state = limiterCache.computeIfAbsent(key, k -> new LimitState());

        long now = System.currentTimeMillis();
        // 检查是否在时间窗口外，如果是则重置计数器
        if (now - state.getLastResetTime() > timeInSeconds * 1000L) {
            synchronized (state) {
                if (System.currentTimeMillis() - state.getLastResetTime() > timeInSeconds * 1000L) {
                    state.getCounter().set(0);
                    state.setLastResetTime(now);
                }
            }
        }

        if (state.getCounter().incrementAndGet() > permits) {
            log.warn("触发限流 [{}], 当前计数: {}, 限制: {}", key, state.getCounter().get(), permits);

            // --- 新增：检查通知冷却时间 ---
            boolean shouldNotify = false;
            if (notificationIntervalSeconds == 0) {
                shouldNotify = true; // 间隔为0，总是通知
            } else {
                // 判断当前时间是否已经超过“上次通知时间 + 间隔”
                if (now - state.getLastNotificationTime() > notificationIntervalSeconds * 1000L) {
                    synchronized (state) {
                        if (System.currentTimeMillis() - state.getLastNotificationTime() > notificationIntervalSeconds * 1000L) {
                            state.setLastNotificationTime(now); // 更新上次通知时间
                            shouldNotify = true;
                        }
                    }
                }
            }

            if (shouldNotify) {
                log.info("满足通知条件，准备抛出 RateLimitException 以发送提示。");
                throw new RateLimitException(limit.message(), bot, event);
            } else {
                log.debug("限流被触发，但处于通知冷却中，仅中断流程不发送提示。");
                // 即使不通知，也要中断流程
                throw new InterruptedException("Rate limit triggered (notification silenced)");
            }
        }
    }

    // 新增：用于存储限流状态的内部类，比 Object[] 更清晰
    private static class LimitState {
        private final AtomicInteger counter = new AtomicInteger(0);
        private volatile long lastResetTime = System.currentTimeMillis();
        private volatile long lastNotificationTime = 0L; // 初始化为0，确保第一次触发时能通知

        public AtomicInteger getCounter() {
            return counter;
        }

        public long getLastResetTime() {
            return lastResetTime;
        }

        public void setLastResetTime(long time) {
            this.lastResetTime = time;
        }

        public long getLastNotificationTime() {
            return lastNotificationTime;
        }

        public void setLastNotificationTime(long time) {
            this.lastNotificationTime = time;
        }
    }

    private Bot findBot(Object[] args) {
        return Arrays.stream(args).filter(Bot.class::isInstance).map(Bot.class::cast).findFirst().orElse(null);
    }

    private MessageEvent findMessageEvent(Object[] args) {
        return Arrays.stream(args).filter(MessageEvent.class::isInstance).map(MessageEvent.class::cast).findFirst().orElse(null);
    }

    private Limit findLimitAnnotation(Method method, Class<?> targetClass) {
        Limit limit = AnnotationUtils.findAnnotation(method, Limit.class);
        if (limit == null) {
            limit = AnnotationUtils.findAnnotation(targetClass, Limit.class);
        }
        return limit;
    }

    private String getSessionIdentifier(MessageEvent event, String methodKey) {
        if (event instanceof GroupMessageEvent groupEvent) {
            return "group:" + groupEvent.getGroupId() + ":" + methodKey;
        } else if (event instanceof PrivateMessageEvent) {
            return "user:" + event.getUserId() + ":" + methodKey;
        } else if (event instanceof AnyMessageEvent anyEvent) {
            if (anyEvent.getGroupId() != null) return "group:" + anyEvent.getGroupId() + ":" + methodKey;
            return "user:" + anyEvent.getUserId() + ":" + methodKey;
        }
        return "user:" + event.getUserId() + ":" + methodKey;
    }
}