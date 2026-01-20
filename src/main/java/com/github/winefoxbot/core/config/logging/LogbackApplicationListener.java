// 文件路径: com/github/winefoxbot/core/config/logging/LogbackApplicationListener.java

package com.github.winefoxbot.core.config.logging;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;

/**
 * 这是一个关键的桥接器，用于在Spring Boot应用完全就绪后，
 * 将可用的ApplicationContext实例传递给静态的WebSocketLogAppender。
 * 这会触发Appender从“缓存模式”切换到“实时发送模式”，并安全地刷新所有缓存的日志。
 */
public class LogbackApplicationListener implements ApplicationListener<ApplicationReadyEvent> { // <-- 修改泛型

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) { // <-- 修改参数类型
        // 在这个阶段，Spring应用已经完全启动并准备好处理请求。
        // 所有的bean都已经被初始化，此时调用 getBean 是安全的。
        System.out.println("Logback bridge: ApplicationReadyEvent received. Setting ApplicationContext for WebSocketLogAppender.");
        WebSocketLogAppender.setApplicationContext(event.getApplicationContext());
    }
}
