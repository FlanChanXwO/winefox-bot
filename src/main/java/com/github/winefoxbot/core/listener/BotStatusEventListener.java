package com.github.winefoxbot.core.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.winefoxbot.core.config.app.WineFoxBotProperties;
import com.github.winefoxbot.core.model.dto.RestartInfo;
import com.github.winefoxbot.core.model.enums.MessageType;
import com.github.winefoxbot.core.service.shiro.ShiroBotsService;
import com.github.winefoxbot.core.service.update.GitHubUpdateService;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.core.CoreEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;

@Primary
@Component
@RequiredArgsConstructor
@Slf4j
public class BotStatusEventListener extends CoreEvent {

    private final ShiroBotsService shiroBotsService;
    private final WineFoxBotProperties wineFoxBotProperties;
    private final ObjectMapper objectMapper;
    private final GitHubUpdateService updateService;
    private final AtomicBoolean restartNoticeSent = new AtomicBoolean(false);
    private static final String RESTART_INFO_FILE = "restart-info.json";

    @Override
    public void online(Bot bot) {
        // 1. 首先，执行原来的上线通知逻辑
        log.info("Bot {} 上线了！", bot.getSelfId());
        for (Long superuser : wineFoxBotProperties.getRobot().getSuperUsers()) {
            bot.sendPrivateMsg(superuser, "我上线啦～", false);
        }
        // 2. 接着，处理重启成功的通知逻辑
        handleRestartNotice(bot);
        // 3. 更新或储存Bot的登录信息
        saveOrUpdateBotInfo(bot);
    }

    /**
     * 保存或更新 Bot 信息到数据库
     * @param bot
     */
    @Async
    protected void saveOrUpdateBotInfo(Bot bot) {
        log.info("正在保存或更新 Bot {} 的信息...", bot.getSelfId());
        if (shiroBotsService.saveOrUpdateBotInfo(bot)) {
            log.info("Bot {} 的信息已保存或更新。", bot.getSelfId());
        } else {
            log.warn("Bot {} 的信息保存或更新失败！", bot.getSelfId());
        }
    }

    /**
     * 检查并发送重启成功通知
     * @param bot 当前上线的 Bot 实例
     */
    @Async
    protected void handleRestartNotice(Bot bot) {
        // 如果已经发送过通知，则直接返回
        if (!restartNoticeSent.compareAndSet(false, true)) {
            return;
        }

        File restartInfoFile = new File(RESTART_INFO_FILE);
        if (!restartInfoFile.exists()) {
            return; // 没有重启文件，正常启动
        }

        log.info("检测到重启信息文件，准备发送重启成功通知...");

        try {
            // 读取文件
            RestartInfo restartInfo = objectMapper.readValue(restartInfoFile, RestartInfo.class);

            // 计算耗时
            long durationMillis = System.currentTimeMillis() - restartInfo.getStartTimeMillis();
            double durationSeconds = durationMillis / 1000.0;

            // 格式化最终消息
            String finalMessage = restartInfo.getSuccessMessage()
                    .replace("{duration}", String.format("%.2f秒", durationSeconds))
                    .replace("{version}",wineFoxBotProperties.getApp().getVersion());

            // 发送消息
            if (MessageType.GROUP.equals(restartInfo.getMessageType())) {
                bot.sendGroupMsg(restartInfo.getTargetId(), finalMessage, false);
                log.info("已向群 {} 发送重启成功通知", restartInfo.getTargetId());
            } else {
                bot.sendPrivateMsg(restartInfo.getTargetId(), finalMessage, false);
                log.info("已向用户 {} 发送重启成功通知", restartInfo.getTargetId());
            }
        } catch (Exception e) {
            log.error("处理重启通知失败", e);
        } finally {
            // 无论成功与否，都删除该文件
            try {
                Files.delete(Paths.get(RESTART_INFO_FILE));
                log.info("重启信息文件已删除。");
            } catch (Exception e) {
                log.error("删除重启信息文件失败", e);
            }
        }
    }


    @Override
    public void offline(long account) {
        // 客户端离线事件
        log.warn("Bot {} 离线了", account);
    }

    @Override
    public boolean session(WebSocketSession session) {
        // 可以通过 session.getHandshakeHeaders().getFirst("x-self-id") 获取上线的机器人账号
        // 例如当服务端为开放服务时，并且只有白名单内的账号才允许连接，此时可以检查账号是否存在于白名内
        // 不存在的话返回 false 即可禁止连接
        return true;
    }

}
