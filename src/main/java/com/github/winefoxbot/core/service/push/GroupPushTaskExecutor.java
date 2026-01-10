package com.github.winefoxbot.core.service.push;

import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.core.BotContainer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

/**
 * 统一的群组推送任务执行器
 * 负责Bot选择、群组成员校验以及失败重试
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GroupPushTaskExecutor {

    private final BotContainer botContainer;

    /**
     * 执行群组推送任务
     *
     * @param groupId  目标群组ID
     * @param taskName 任务名称（用于日志）
     * @param task     实际的推送逻辑，接受一个Bot实例
     */
    public void execute(Long groupId, String taskName, Consumer<Bot> task) {
        log.info("准备执行推送任务: [{}], 群组: [{}]", taskName, groupId);

        Bot bot = findBotForGroup(groupId);
        if (bot == null) {
            log.warn("任务 [{}] 取消: 未找到任何Bot在群组 [{}] 中，或者Bot未连接。", taskName, groupId);
            return;
        }

        executeWithRetry(bot, groupId, taskName, task);
    }

    /**
     * 查找在指定群组中的Bot
     */
    private Bot findBotForGroup(Long groupId) {
        if (botContainer.robots == null || botContainer.robots.isEmpty()) {
            return null;
        }

        // 优先查找已经加入该群的Bot
        // 这里的逻辑可以优化，目前简单遍历所有在线Bot检查群列表
        // 注意：getGroupInfo可能会有网络请求，如果Bot很多可能会慢。
        // 考虑到通常Bot数量较少，暂且如此。
        for (Bot bot : botContainer.robots.values()) {
            try {
                // 使用缓存查询，减少网络IO
                if (bot.getGroupInfo(groupId, false).getRetCode() == 0) {
                    return bot;
                }
            } catch (Exception e) {
                log.warn("检查Bot [{}] 群组 [{}] 状态时发生错误: {}", bot.getSelfId(), groupId, e.getMessage());
            }
        }
        return null;
    }

    private void executeWithRetry(Bot bot, Long groupId, String taskName, Consumer<Bot> task) {
        int maxRetries = 3;
        int delayMs = 2000;

        for (int i = 0; i < maxRetries; i++) {
            try {
                task.accept(bot);
                log.info("任务 [{}] 推送成功, 群组: [{}]", taskName, groupId);
                return;
            } catch (Exception e) {
                log.error("任务 [{}] 推送失败 (尝试 {}/{}), 群组: [{}], 错误: {}",
                        taskName, i + 1, maxRetries, groupId, e.getMessage());

                if (i < maxRetries - 1) {
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
        log.error("任务 [{}] 经过 {} 次重试后最终失败, 群组: [{}]", taskName, maxRetries, groupId);
        // 可以在这里添加告警，发给管理员等
    }
}

