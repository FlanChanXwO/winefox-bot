package com.github.winefoxbot.core.config.logging; // 放在同一个包下，便于管理

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

/**
 * 这是一个标准的 Spring Bean，它的作用是监听 Spring 容器的启动完成事件，
 * 然后调用 WebSocketLogAppender 的静态方法，将上下文传递过去，从而搭好桥梁。
 */
@Component
public class LogbackContextInitializer implements ApplicationListener<ContextRefreshedEvent> {

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        // 获取当前的 ApplicationContext
        ApplicationContext applicationContext = event.getApplicationContext();
        
        // 调用静态方法，将上下文设置到我们的 Appender 桥接类中
        WebSocketLogAppender.setApplicationContext(applicationContext);
    }
}
