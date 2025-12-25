package com.github.winefoxbot;

import com.github.winefoxbot.init.NoneBot2InitializeExecutor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.ai.model.chat.client.autoconfigure.ChatClientAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.concurrent.CompletableFuture;

@EnableAsync
@EnableScheduling
@EnableAspectJAutoProxy(proxyTargetClass = true)
@MapperScan("com.github.winefoxbot.mapper")
@SpringBootApplication(exclude = ChatClientAutoConfiguration.class)
public class WineFoxBotApp {

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(WineFoxBotApp.class, args);

        // 使用异步方式执行任务，确保不会阻塞主线程
        Runnable nonebot2Task = () -> {
            if (ctx.containsBean(NoneBot2InitializeExecutor.class.getSimpleName())) {
                NoneBot2InitializeExecutor noneBot2InitializeExecutor = ctx.getBean(NoneBot2InitializeExecutor.class);
                try {
                    noneBot2InitializeExecutor.execute();
                } catch (Exception e) {
                    e.printStackTrace(); // 直接打印异常信息
                }
            }
        };

        // 异步执行任务，避免阻塞
        CompletableFuture.runAsync(nonebot2Task);
    }

}
