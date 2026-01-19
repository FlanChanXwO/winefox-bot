package com.github.winefoxbot.core.aop.aspect;

import com.github.winefoxbot.core.annotation.common.RedissonLock;
import com.github.winefoxbot.core.constants.CacheConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * @author FlanChan
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RedissonLockAspect {

    private final RedissonClient redissonClient;
    private final ExpressionParser parser = new SpelExpressionParser();
    private final DefaultParameterNameDiscoverer nameDiscoverer = new DefaultParameterNameDiscoverer();

    @Around("@annotation(com.github.winefoxbot.core.annotation.common.RedissonLock)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        RedissonLock redissonLock = findRedissionLockAnnotation(joinPoint);
        String lockKey = generateKey(joinPoint, redissonLock);
        RLock lock = redissonClient.getLock(lockKey);

        boolean isLocked = false;
        try {
            // 尝试获取锁
            isLocked = lock.tryLock(redissonLock.waitTime(), redissonLock.leaseTime(), redissonLock.unit());
            if (isLocked) {
                return joinPoint.proceed();
            } else {
                log.warn("Failed to acquire lock for key: {}", lockKey);
                // 根据业务需求，这里可以抛出异常或者直接返回
                // throw new RuntimeException("系统繁忙，请稍后再试");
                return null; 
            }
        } finally {
            if (isLocked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }


    private RedissonLock findRedissionLockAnnotation(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        return AnnotationUtils.findAnnotation(method, RedissonLock.class);
    }



    private String generateKey(ProceedingJoinPoint joinPoint, RedissonLock redissonLock) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String[] paramNames = nameDiscoverer.getParameterNames(method);
        Object[] args = joinPoint.getArgs();

        EvaluationContext context = new StandardEvaluationContext();
        if (paramNames != null) {
            for (int i = 0; i < paramNames.length; i++) {
                context.setVariable(paramNames[i], args[i]);
            }
        }
        
        // 解析 SpEL
        String keyPart = parser.parseExpression(redissonLock.key()).getValue(context, String.class);
        
        return "%s:%s:%s".formatted(CacheConstants.CACHE_KEY_PREFIX, redissonLock.prefix(), keyPart);
    }
}
