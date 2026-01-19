package com.github.winefoxbot;

import com.github.winefoxbot.core.config.logging.LogbackApplicationListener;
import com.github.winefoxbot.core.config.logging.LogbackContextInitializer;
import com.github.winefoxbot.core.init.ScriptChecker;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * WineFoxBot Application
 * @author FlanChan
 * @since 2025/11/1
 */
@EnableAsync
@EnableScheduling
@EnableAspectJAutoProxy(proxyTargetClass = true)
@MapperScan({
        "com.github.winefoxbot.core.mapper",
        "com.github.winefoxbot.plugins.**.mapper"
})
@SpringBootApplication
@EnableCaching
public class WineFoxBotApp {

    public static void main(String[] args) {
        ScriptChecker.checkAndDeploy(WineFoxBotApp.class);
        SpringApplication app = new SpringApplication(WineFoxBotApp.class);
        app.addInitializers(new LogbackContextInitializer());
        app.addListeners(new LogbackApplicationListener());
        app.setApplicationStartup(new BufferingApplicationStartup(1000));
        app.run(args);
    }
}
