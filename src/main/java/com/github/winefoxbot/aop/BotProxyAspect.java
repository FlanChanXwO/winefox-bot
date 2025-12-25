package com.github.winefoxbot.aop;

import com.github.winefoxbot.aop.interceptor.BotSendMsgInterceptor;
import com.mikuac.shiro.core.Bot;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.objenesis.Objenesis;
import org.objenesis.ObjenesisStd;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.cglib.proxy.Callback;
import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.Factory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(12)
public class BotProxyAspect {

    private static final Objenesis OBJENESIS = new ObjenesisStd();

    private final ApplicationContext applicationContext;

    @Autowired
    public BotProxyAspect(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Pointcut("@within(com.mikuac.shiro.annotation.common.Shiro) && execution(public * *(..))")
    public void methodWithBotAsParam() {}

    @Around("methodWithBotAsParam()")
    public Object proxyBotParameter(ProceedingJoinPoint pjp) throws Throwable {
        Object[] args = pjp.getArgs();
        int botIndex = -1;

        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof Bot) {
                botIndex = i;
                break;
            }
        }

        if (botIndex != -1) {
            final Bot originalBot = (Bot) args[botIndex];

            BotSendMsgInterceptor interceptor = applicationContext.getBean(BotSendMsgInterceptor.class);
            //手动调用setter，将运行时的Bot对象注入进去
            interceptor.setOriginalBot(originalBot);

            Enhancer enhancer = new Enhancer();
            enhancer.setSuperclass(Bot.class);
            enhancer.setCallbackType(interceptor.getClass());

            Class<?> proxyClass = enhancer.createClass();
            Bot proxyBot = (Bot) OBJENESIS.newInstance(proxyClass);

            ((Factory) proxyBot).setCallbacks(new Callback[]{interceptor});

            args[botIndex] = proxyBot;
        }

        return pjp.proceed(args);
    }
}

