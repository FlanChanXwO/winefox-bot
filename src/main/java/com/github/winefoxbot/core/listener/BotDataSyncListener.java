package com.github.winefoxbot.core.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.winefoxbot.core.config.app.WineFoxBotProperties;
import com.github.winefoxbot.core.event.BotOfflineEvent;
import com.github.winefoxbot.core.event.BotOnlineEvent;
import com.github.winefoxbot.core.model.dto.RestartInfo;
import com.github.winefoxbot.core.model.entity.ShiroFriends;
import com.github.winefoxbot.core.model.entity.ShiroGroup;
import com.github.winefoxbot.core.model.enums.common.ConnectionEventType;
import com.github.winefoxbot.core.model.enums.common.MessageType;
import com.github.winefoxbot.core.service.connectionlogs.WinefoxBotConnectionLogsService;
import com.github.winefoxbot.core.service.shiro.ShiroBotsService;
import com.github.winefoxbot.core.service.shiro.ShiroFriendsService;
import com.github.winefoxbot.core.service.shiro.ShiroGroupsService;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.action.common.ActionData;
import com.mikuac.shiro.dto.action.common.ActionList;
import com.mikuac.shiro.dto.action.response.FriendInfoResp;
import com.mikuac.shiro.dto.action.response.GroupInfoResp;
import com.mikuac.shiro.dto.action.response.StrangerInfoResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@Slf4j
@RequiredArgsConstructor
public class BotDataSyncListener {
    private final ShiroBotsService shiroBotsService;

    private final WineFoxBotProperties wineFoxBotProperties;
    private final WinefoxBotConnectionLogsService connectionLogsService;
    private final ShiroGroupsService shiroGroupsService;
    private final ShiroFriendsService shiroFriendsService;

    private final ObjectMapper objectMapper;
    private final AtomicBoolean restartNoticeSent = new AtomicBoolean(false);

    private static final String RESTART_INFO_FILE = "restart-info.json";


    @Async
    @EventListener
    public void handleBotOnlineSync(BotOnlineEvent event) {
        Bot bot = event.getBot();
        log.info("收到 Bot {} 上线事件，开始异步同步群组和好友信息...", bot.getSelfId());

        try {
            // 记录连接日志
            connectionLogsService.saveLog(bot.getSelfId(), ConnectionEventType.CONNECT);
            // 1. 首先，执行上线通知逻辑
            noticeSuperUsers(bot);
            // 2. 接着，处理重启成功的通知逻辑
            handleRestartNotice(bot);
            // 3. 更新或储存Bot的登录信息
            saveOrUpdateBotInfo(bot);
            // 4. 刷新群组和好友信息
            syncGroups(bot);
            syncFriends(bot);
        } catch (Exception e) {
            log.error("同步 Bot {} 信息时发生异常", bot.getSelfId(), e);
        }
    }

    private void noticeSuperUsers(Bot bot) {
        for (Long superuser : wineFoxBotProperties.getRobot().getSuperUsers()) {
            ActionData<StrangerInfoResp> strangerInfo = bot.getStrangerInfo(superuser, false);
            if (strangerInfo != null && strangerInfo.getRetCode() == 0) {
                bot.sendPrivateMsg(superuser, "我上线啦～", false);
            }
        }
    }

    private void syncGroups(Bot bot) {
        long selfId = bot.getSelfId();
        // 获取群列表可能耗时，网络IO
        Optional<ActionList<GroupInfoResp>> groupListOpt = Optional.ofNullable(bot.getGroupList());

        if (groupListOpt.isPresent()) {
            List<GroupInfoResp> data = groupListOpt.get().getData();
            if (data == null) {
                log.warn("Bot {} 获取群列表时返回空数据", selfId);
                return;
            }

            List<ShiroGroup> list = data.stream()
                    .map(e -> {
                        ShiroGroup g = ShiroGroup.convertToShiroGroup(e, selfId);
                        // 建议: 在这里统一设置更新时间，不要依赖数据库默认值
                        g.setLastUpdated(LocalDateTime.now());
                        return g;
                    })
                    .toList();
            
            if (list.isEmpty()) return;
            int count = shiroGroupsService.saveOrUpdateBatchGroups(list);
            log.info("异步任务完成: 已保存或更新 {} 个群组信息。", count);
        }
    }

    private void syncFriends(Bot bot) {
        long selfId = bot.getSelfId();
        Optional<ActionList<FriendInfoResp>> friendListOpt = Optional.ofNullable(bot.getFriendList());

        if (friendListOpt.isPresent()) {
            List<FriendInfoResp> data = friendListOpt.get().getData();
            if (data == null) {
                log.warn("Bot {} 获取好友列表时返回空数据", selfId);
                return;
            }

            List<ShiroFriends> list = data.stream()
                    .map(e -> ShiroFriends.convertToShiroFriend(e, selfId))
                    .toList();
            
            if (list.isEmpty()) return;

            int count = shiroFriendsService.saveOrUpdateBatchFriends(list);
            log.info("异步任务完成: 已保存或更新 {} 个好友信息。", count);
        }
    }



    @Async
    @EventListener
    public void handleBotOfflineSync(BotOfflineEvent event) {
        log.info("收到 Bot {} 离线事件，开始异步记录离线状日志...", event.getAccount());
        try {
            // 记录断开连接日志
            connectionLogsService.saveLog(event.getAccount(), ConnectionEventType.DISCONNECT);
        } catch (Exception e) {
            log.error("同步 Bot {} 信息时发生异常",event.getAccount(), e);
        }
    }


    /**
     * 保存或更新 Bot 信息到数据库
     */
    private void saveOrUpdateBotInfo(Bot bot) {
        log.info("正在保存或更新 Bot {} 的信息...", bot.getSelfId());
        if (shiroBotsService.saveOrUpdateBotInfo(bot)) {
            log.info("Bot {} 的信息已保存或更新。", bot.getSelfId());
        } else {
            log.warn("Bot {} 的信息保存或更新失败！", bot.getSelfId());
        }
        // 记录连接日志
        connectionLogsService.saveLog(bot.getSelfId(), ConnectionEventType.CONNECT);
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

            // 格式化最终消息
            String finalMessage = restartInfo.getSuccessMessage()
                    .replace("{duration}", String.format("%.2f秒", durationSeconds))
                    .replace("{version}" , 'v' + wineFoxBotProperties.getApp().getVersion());

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
}
