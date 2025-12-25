// file: com/github/winefoxbotbackend/schedule/task/PixivRankPushExecutor.java

package com.github.winefoxbot.schedule.task;
import com.github.winefoxbot.model.dto.pixiv.PixivDetail;
import com.github.winefoxbot.model.dto.pixiv.PixivPushTarget;
import com.github.winefoxbot.service.pixiv.PixivRankService;
import com.github.winefoxbot.service.pixiv.PixivService;
import com.github.winefoxbot.utils.FileUtil;
import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.common.utils.ShiroUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.core.BotContainer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component("pixivRankPushExecutor") // 为这个执行器指定一个清晰的、唯一的Bean名称
@RequiredArgsConstructor
public class PixivRankPushExecutor implements TaskExecutor<PixivPushTarget> {
    private final BotContainer botContainer;
    private final PixivService pixivService;
    private final PixivRankService pixivRankService;

    @Override
    public void execute(PixivPushTarget params) {
        try {
            // 直接调用类型安全的方法，不再需要反射！
            this.pushDailyRankToTarget(params);
        } catch (Exception e) {
            // 建议在这里处理异常或重新抛出为运行时异常
            throw new RuntimeException("执行Pixiv排行推送任务失败，参数: " + params, e);
        }
    }


    /**
     * 【调度任务执行目标方法】
     * 执行推送Pixiv每日排行榜到指定目标。
     * 此方法由 TaskDispatcherService 通过反射调用。
     * @param target 推送目标信息
     */
    public void pushDailyRankToTarget(PixivPushTarget target) throws Exception {
        log.info("开始执行Pixiv排行推送任务，目标: {}", target);

        // 从容器中获取一个可用的 Bot 实例
        Optional<Bot> botOpt = botContainer.robots.values().stream().findFirst();
        if (botOpt.isEmpty()) {
            log.error("无法执行推送任务，因为没有可用的 Bot 实例在线。");
            return;
        }
        Bot bot = botOpt.get();

        // 复用获取排行榜的逻辑，并发送
        try {
            List<String> msgList = getRankMessages(bot);
            if (msgList.isEmpty()) {
                log.warn("获取排行榜内容为空，本次不推送。目标: {}", target);
                return;
            }

            List<Map<String, Object>> forwardMsg = ShiroUtils.generateForwardMsg(bot.getSelfId(), "P站本日排行榜", msgList);

            if ("group".equalsIgnoreCase(target.getTargetType())) {
                bot.sendGroupForwardMsg(target.getTargetId(), forwardMsg);
            } else {
                bot.sendPrivateForwardMsg(target.getTargetId(), forwardMsg);
            }
            log.info("成功向 {}[{}] 推送了Pixiv排行榜。", target.getTargetType(), target.getTargetId());

        } catch (Exception e) {
            log.error("执行Pixiv排行推送任务失败。目标: {}", target, e);
            // 可以在这里向管理员发送失败通知
        }
    }


    /**
     * 辅助方法：获取排行榜并生成消息列表（从 getRankToday 方法中提取的逻辑）
     */
    private List<String> getRankMessages(Bot bot) throws Exception {
        List<String> msgList = new ArrayList<>();
        List<String> rankIds = pixivRankService.getRank(PixivRankService.Mode.DAILY, PixivRankService.Content.ILLUST, false);

        List<File> allFiles = new ArrayList<>();

        for (String rankId : rankIds) {
            List<File> files = pixivService.fetchImages(rankId).join();
            if (files.isEmpty()) continue;
            allFiles.addAll(files);

            PixivDetail pixivDetail = pixivService.getPixivArtworkDetail(rankId);
            MsgUtils builder = MsgUtils.builder();
            builder.text(String.format("""
                            作品标题：%s (%s)
                            作者：%s (%s)
                            作品链接：https://www.pixiv.net/artworks/%s
                            标签：%s
                            """, pixivDetail.getTitle(), pixivDetail.getPid(),
                    pixivDetail.getUserName(), pixivDetail.getUid(),
                    pixivDetail.getPid(),
                    org.springframework.util.StringUtils.collectionToCommaDelimitedString(pixivDetail.getTags())));
            for (File file : files) {
                String filePath = FileUtil.getFileUrlPrefix() + file.getAbsolutePath();
                builder.img(filePath);
            }
            msgList.add(builder.build());
        }

        // 异步删除文件
        new Thread(() -> {
            try {
                // 等待一小段时间确保消息发出
                Thread.sleep(60 * 1000);
                for (File file : allFiles) {
                    if (file.exists()) {
                        FileUtils.deleteDirectory(file.getParentFile());
                    }
                }
            } catch (Exception e) {
                log.error("删除Pixiv排行榜临时文件失败", e);
            }
        }).start();

        return msgList;
    }

    @Override
    public Class<PixivPushTarget> getParameterType() {
        // 返回类型安全的Class对象
        return PixivPushTarget.class;
    }
}
