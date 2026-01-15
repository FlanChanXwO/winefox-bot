package com.github.winefoxbot.core.config.logging;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.encoder.Encoder;
import com.github.winefoxbot.core.service.logging.WebSocketLogService;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.nio.charset.StandardCharsets;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 带有缓存功能的 WebSocket Appender。
 * 在 Spring 上下文就绪前，它会将日志事件缓存到内存队列中。
 * 上下文就绪后，它会一次性发送所有缓存的日志，并切换到实时发送模式。
 */
public class WebSocketLogAppender extends AppenderBase<ILoggingEvent> {

    // 1. 核心组件：一个线程安全的队列来缓存早期日志事件
    private static final Queue<ILoggingEvent> eventCache = new ConcurrentLinkedQueue<>();

    // 2. 状态开关：一个原子布尔值，标记 Spring 上下文是否已就绪
    private static final AtomicBoolean contextReady = new AtomicBoolean(false);

    private static ApplicationContext applicationContext;
    private Encoder<ILoggingEvent> encoder;

    /**
     * 由 Spring 端的 Initializer 调用，这是连接两个世界的关键方法。
     * @param context Spring ApplicationContext
     */
    public static void setApplicationContext(ApplicationContext context) {
        applicationContext = context;
        // 原子地设置状态为 true，并返回之前的值。这可以防止 flush 被多次调用。
        if (contextReady.compareAndSet(false, true)) {
            System.out.println("Logback bridge: Spring Context is ready. Flushing cached logs...");
            flushCache();
        }
    }

    /**
     * 清理并发送缓存的日志。
     */
    private static void flushCache() {
        if (applicationContext == null) {
            return;
        }
        // 从 Spring 容器中获取 Service Bean
        WebSocketLogService logService = applicationContext.getBean(WebSocketLogService.class);
        Encoder<ILoggingEvent> staticEncoder = findEncoderInContext();

        if (logService == null || staticEncoder == null) {
            System.err.println("WebSocketLogAppender: Could not find LogService or Encoder. Cached logs will not be sent.");
            return;
        }

        // 遍历并发送队列中的所有日志
        ILoggingEvent event;
        while ((event = eventCache.poll()) != null) {
            try {
                byte[] encodedBytes = staticEncoder.encode(event);
                String logMessage = new String(encodedBytes, StandardCharsets.UTF_8);
                logService.sendLog(logMessage);
            } catch (Exception e) {
                // 使用 System.err 避免再次触发日志循环
                System.err.println("Error flushing cached log: " + e.getMessage());
            }
        }
        System.out.println("Logback bridge: " + "Cache flushed.");
    }

    /**
     * 在静态方法中，我们无法访问 this.encoder，所以需要从 Logback 上下文中找到它。
     */
    private static Encoder<ILoggingEvent> findEncoderInContext() {
        try {
            LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
            WebSocketLogAppender appender = (WebSocketLogAppender) loggerContext.getLogger("ROOT").getAppender("WEBSOCKET");
            return appender.getEncoder();
        } catch (Exception e) {
            System.err.println("Failed to find encoder from Logback context: " + e.getMessage());
            return null;
        }
    }

    @Override
    protected void append(ILoggingEvent eventObject) {
        if (!isStarted()) {
            return;
        }

        // 3. 核心逻辑：根据状态决定是缓存还是直接发送
        if (contextReady.get()) {
            // 如果上下文已就绪，直接发送
            sendImmediately(eventObject);
        } else {
            // 否则，加入缓存队列
            eventCache.add(eventObject);
        }
    }

    private void sendImmediately(ILoggingEvent eventObject) {
        if (applicationContext == null) return;
        try {
            WebSocketLogService logService = applicationContext.getBean(WebSocketLogService.class);
            if (logService != null && this.encoder != null) {
                byte[] encodedBytes = this.encoder.encode(eventObject);
                String logMessage = new String(encodedBytes, StandardCharsets.UTF_8);
                logService.sendLog(logMessage);
            }
        } catch (Exception e) {
            addError("Error while sending log via WebSocket.", e);
        }
    }

    public Encoder<ILoggingEvent> getEncoder() {
        return encoder;
    }

    public void setEncoder(Encoder<ILoggingEvent> encoder) {
        this.encoder = encoder;
    }
}
