package com.github.winefoxbot;

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
 * @author FlanChan
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
        // 在启动Spring之前，首先运行脚本检查器
        // 它会判断运行环境，如果需要，会生成脚本并直接退出程序
        ScriptChecker.checkAndDeploy(WineFoxBotApp.class);
        // 如果程序能运行到这里，说明：
        // 1. 是在IDE中运行的
        // 2. 或者脚本文件已经存在，不需要生成和退出
        // 此时，正常启动Spring应用
        SpringApplication app = new SpringApplication(WineFoxBotApp.class);
        app.setApplicationStartup(new BufferingApplicationStartup(1000));
        app.run(args);
    }
}
