package com.github.winefoxbot.core.plugins;

import com.github.winefoxbot.core.annotation.PluginFunction;
import com.github.winefoxbot.core.model.enums.Permission;
import com.mikuac.shiro.annotation.AnyMessageHandler;
import com.mikuac.shiro.annotation.MessageHandlerFilter;
import com.mikuac.shiro.annotation.common.Shiro;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.AnyMessageEvent;
import com.mikuac.shiro.enums.MsgTypeEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Shiro
@Component
@Slf4j
public class WaitInputPlugin {

    // --- 配置区域 ---
    // 会话超时时间 (毫秒)，这里设置为 30 秒
    private static final long SESSION_TIMEOUT_MS = 30 * 1000L;


    // 存储会话数据的包装类
    @Getter
    private static class SessionData {
        private final List<String> params = new ArrayList<>();
        private long lastActiveTime = System.currentTimeMillis();

        // 更新最后活跃时间
        public void refresh() {
            this.lastActiveTime = System.currentTimeMillis();
        }

        // 检查是否过期
        public boolean isExpired() {
            return System.currentTimeMillis() - this.lastActiveTime > SESSION_TIMEOUT_MS;
        }
    }

    // 缓存：UserId -> SessionData
    private final Map<Long, SessionData> sessionCache = new ConcurrentHashMap<>();

    // 定时清理器 (单线程即可)
    private final ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor();

    // 构造函数：初始化时启动定时清理任务
    public WaitInputPlugin() {
        // 每 5 秒执行一次清理扫描
        cleaner.scheduleAtFixedRate(() -> {
            try {
                sessionCache.entrySet().removeIf(entry -> {
                    boolean expired = entry.getValue().isExpired();
                    if (expired) {
                        log.info("用户 {} 的会话已超时，自动清理。", entry.getKey());
                    }
                    return expired;
                });
            } catch (Exception e) {
                log.error("清理过期会话时发生异常", e);
            }
        }, 5, 5, TimeUnit.SECONDS);
    }

    // --- 业务逻辑 ---

    @PluginFunction(name = "参数收集测试", description = "测试多步输入", permission = Permission.USER)
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = "^/collect_params$")
    public void startCollection(Bot bot, AnyMessageEvent event) {
        long userId = event.getUserId();

        // 覆盖旧会话，开启新会话
        sessionCache.put(userId, new SessionData());

        bot.sendMsg(event, "已开启参数收集模式！(30秒内无操作将失效)\n请输入第 1 个参数：", false);
    }

    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text)
    public void handleInput(Bot bot, AnyMessageEvent event) {
        long userId = event.getUserId();

        // 1. 快速检查：如果不在缓存里，直接忽略
        if (!sessionCache.containsKey(userId)) {
            return;
        }

        SessionData session = sessionCache.get(userId);
        String content = event.getMessage();

        // 2. 避免指令本身被录入
        if (content.startsWith("/")) {
            return;
        }

        // 3. 检查是否过期 (防止定时任务还没跑，但时间已经到了)
        if (session.isExpired()) {
            sessionCache.remove(userId);
            bot.sendMsg(event, "会话已超时，请重新输入 /collect_params 开始。", false);
            return;
        }

        // 4. 更新活跃时间
        session.refresh();

        // 5. 业务逻辑
        List<String> params = session.getParams();
        params.add(content);
        int currentSize = params.size();

        if (currentSize < 3) {
            // 使用 .formatted() 替代 JDK 21 preview 的 STR.
            String msg = "收到参数 %d: %s\n请输入第 %d 个参数：".formatted(currentSize, content, currentSize + 1);
            bot.sendMsg(event, msg, false);
        } else {
            try {
                String result = "收集完成！\n你的参数数组为: %s".formatted(params.toString());
                bot.sendMsg(event, result, false);
            } finally {
                // 完成后移除会话
                sessionCache.remove(userId);
            }
        }
    }
}
