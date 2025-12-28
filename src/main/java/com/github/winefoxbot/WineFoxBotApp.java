package com.github.winefoxbot;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.ai.model.chat.client.autoconfigure.ChatClientAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableAsync
@EnableScheduling
@EnableAspectJAutoProxy(proxyTargetClass = true)
@MapperScan("com.github.winefoxbot.mapper")
@SpringBootApplication(exclude = ChatClientAutoConfiguration.class)
@EnableCaching
public class WineFoxBotApp {

    public static void main(String[] args) {
        SpringApplication.run(WineFoxBotApp.class, args);
    }
}
