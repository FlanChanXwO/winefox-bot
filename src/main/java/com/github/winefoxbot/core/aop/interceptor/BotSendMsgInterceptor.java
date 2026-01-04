package com.github.winefoxbot.core.aop.interceptor;

import com.github.winefoxbot.core.aop.handler.BotSendMsgHandler;
import com.mikuac.shiro.core.Bot;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.cglib.proxy.MethodInterceptor;
import org.springframework.cglib.proxy.MethodProxy;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Component
@Slf4j
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class BotSendMsgInterceptor implements MethodInterceptor {

    @Setter
    private Bot originalBot;

    // Spring will inject the singleton handler here
    @Autowired
    private BotSendMsgHandler botSendMsgHandler;

    @Override
    public Object intercept(Object obj, Method method, Object[] methodArgs, MethodProxy proxy) throws Throwable {
        if (this.originalBot == null) {
            throw new IllegalStateException("originalBot has not been set! Interceptor was not configured correctly.");
        }

        // Proceed with the original method call first
        Object result =  method.invoke(originalBot, methodArgs);
        log.info("Bot Sending Message -> Method Name: " + method.getName());
        switch (method.getName()) {
            case "sendMsg" -> botSendMsgHandler.handleAnyMessageEvent(originalBot, methodArgs, result);
            case "sendGroupMsg" -> botSendMsgHandler.handleGroupMessageEvent(originalBot, methodArgs, result);
            case "sendPrivateMsg" -> botSendMsgHandler.handlePrivateMessageEvent(originalBot, methodArgs, result);
            default -> log.warn("BotSendMsgInterceptor intercepted an unexpected method: " + method.getName());
        }
        return result;
    }
}