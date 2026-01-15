// 文件: com/github/winefoxbot/core/config/logging/LogbackContextInitializer.java
package com.github.winefoxbot.core.config.logging;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.util.StatusPrinter;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.ResourceUtils;

import java.io.IOException;
import java.net.URL;

/**
 * 这是一个非常早期的初始化器，用于在Spring Boot自动配置日志系统之前，
 * 手动加载和配置Logback。这确保了从应用启动开始，我们使用的就是同一个
 * LoggerContext和Appender实例，从而防止早期日志的丢失。
 */
public class LogbackContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    // 日志属性，用于“锁定”日志配置，防止Spring Boot再次重置
    private static final String LOGGING_CONFIG_PROPERTY = "logging.config";

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        // 检查是否已经配置过，防止重复执行
        if (System.getProperty(LOGGING_CONFIG_PROPERTY) != null) {
            return;
        }

        try {
            ILoggerFactory loggerFactory = LoggerFactory.getILoggerFactory();
            if (!(loggerFactory instanceof LoggerContext)) {
                // 如果SLF4J绑定的不是Logback，则此初始化器无效
                System.err.println("LogbackContextInitializer: LoggerFactory is not a Logback LoggerContext. Skipping initialization.");
                return;
            }

            LoggerContext loggerContext = (LoggerContext) loggerFactory;
            // 停止现有的默认上下文，准备重新配置
            loggerContext.stop();

            JoranConfigurator configurator = new JoranConfigurator();
            configurator.setContext(loggerContext);

            // 明确指定要使用的配置文件
            URL configUrl = new ClassPathResource("logback.xml").getURL();
            configurator.doConfigure(configUrl);
            System.out.println("LogbackContextInitializer: Successfully configured Logback from " + configUrl);

            // 打印内部状态，用于调试
            // StatusPrinter.printInCaseOfErrorsOrWarnings(loggerContext);

            // ** 最关键的一步 **
            // 在环境中设置 logging.config 属性。
            // Spring Boot的LoggingApplicationListener会检查这个属性，
            // 如果发现它已经被设置，就会跳过其自动配置（重置）过程。
            // 我们随便设置一个值，内容不重要，重要的是这个属性存在。
            System.setProperty(LOGGING_CONFIG_PROPERTY, configUrl.toString());

        } catch (IOException | JoranException e) {
            // 在这个阶段，日志系统可能还未完全工作，使用System.err打印错误
            System.err.println("LogbackContextInitializer: Failed to initialize Logback context.");
            e.printStackTrace();
        }
    }
}
