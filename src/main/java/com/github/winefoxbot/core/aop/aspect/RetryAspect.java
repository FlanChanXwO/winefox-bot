package com.github.winefoxbot.core.aop.aspect;

import com.github.winefoxbot.core.annotation.common.Retry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@Aspect
@Component
public class RetryAspect {

    private static final Logger log = LoggerFactory.getLogger(RetryAspect.class);

    @Around("@annotation(retry)")
    public Object around(ProceedingJoinPoint joinPoint, Retry retry) throws Throwable {
        int maxAttempts = retry.maxAttempts();
        long delay = retry.delay();
        TimeUnit timeUnit = retry.timeUnit();
        boolean useExponentialBackoff = retry.exponentialBackoff();
        Class<? extends Throwable>[] retryOn = retry.retryOn();

        Throwable lastException = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                // 尝试执行目标方法
                return joinPoint.proceed();
            } catch (Throwable e) {
                lastException = e;

                // 检查捕获的异常是否在白名单中
                if (!shouldRetry(e, retryOn)) {
                    log.error("Exception {} is not in retry whitelist. Aborting.", e.getClass().getName());
                    throw e;
                }

                log.warn("Method {} failed on attempt {}/{} due to: {}",
                        joinPoint.getSignature().getName(), attempt, maxAttempts, e.getMessage());

                if (attempt == maxAttempts) {
                    break; // 次数耗尽，退出循环处理最终异常
                }

                // 计算等待时间
                long waitTime = useExponentialBackoff ? delay * (long) Math.pow(2, attempt - 1) : delay;

                try {
                    timeUnit.sleep(waitTime);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Retry interrupted", ie);
                }
            }
        }

        // 重试耗尽，抛出指定异常
        throw buildFailureException(retry, lastException);
    }

    // 检查异常是否需要重试
    private boolean shouldRetry(Throwable thrown, Class<? extends Throwable>[] retryOn) {
        return Arrays.stream(retryOn).anyMatch(clazz -> clazz.isInstance(thrown));
    }

    // 构建最终抛出的异常
    private RuntimeException buildFailureException(Retry retry, Throwable cause) {
        String message = retry.failureMessage();
        Class<? extends RuntimeException> exceptionClass = retry.failureException();

        // 如果没指定消息，使用原始异常消息
        String finalMessage = (message != null && !message.isEmpty())
                ? message
                : "Retry failed after " + retry.maxAttempts() + " attempts. Last error: " + cause.getMessage();

        try {
            // 尝试查找带 String 和 Throwable 的构造函数
            Constructor<? extends RuntimeException> constructorWithCause = exceptionClass.getConstructor(String.class, Throwable.class);
            return constructorWithCause.newInstance(finalMessage, cause);
        } catch (Exception e1) {
            try {
                // 回退查找带 String 的构造函数
                Constructor<? extends RuntimeException> constructorMsg = exceptionClass.getConstructor(String.class);
                RuntimeException ex = constructorMsg.newInstance(finalMessage);
                ex.initCause(cause);
                return ex;
            } catch (Exception e2) {
                // 如果反射创建失败，兜底抛出 RuntimeException
                return new RuntimeException(finalMessage, cause);
            }
        }
    }
}
