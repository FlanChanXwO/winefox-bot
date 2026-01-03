package com.github.winefoxbot.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.winefoxbot.config.app.WineFoxBotProperties;
import com.github.winefoxbot.model.dto.core.RestartInfo;
import com.github.winefoxbot.model.enums.MessageType;
import com.github.winefoxbot.service.github.GitHubUpdateService;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.core.CoreEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
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


    private final WineFoxBotProperties wineFoxBotProperties;
    private final ObjectMapper objectMapper;
    private final GitHubUpdateService updateService;

    private static final String RESTART_INFO_FILE = "restart-info.json";
    // 添加一个标志位，确保重启通知只被发送一次
    private final AtomicBoolean restartNoticeSent = new AtomicBoolean(false);
    @Override
    public void online(Bot bot) {
        // 1. 首先，执行原来的上线通知逻辑
        log.info("Bot {} 上线了！", bot.getSelfId());
        for (Long superuser : wineFoxBotProperties.getSuperusers()) {
            bot.sendPrivateMsg(superuser, "我上线啦～", false);
        }

        // 2. 接着，处理重启成功的通知逻辑
        handleRestartNotice(bot);
    }

    /**
     * 检查并发送重启成功通知
     * @param bot 当前上线的 Bot 实例
     */
    private void handleRestartNotice(Bot bot) {
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

            // 获取当前版本
            String currentVersion = updateService.getCurrentVersionInfo().toString();

            // 格式化最终消息
            String finalMessage = restartInfo.getSuccessMessage()
                    .replace("{duration}", String.format("%.2f秒", durationSeconds))
                    .replace("{version}",wineFoxBotProperties.getVersion() + " " + currentVersion);

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
