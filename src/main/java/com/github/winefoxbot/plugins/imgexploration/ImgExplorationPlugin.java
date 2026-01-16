package com.github.winefoxbot.plugins.imgexploration;

import com.github.winefoxbot.core.annotation.Plugin;
import com.github.winefoxbot.core.annotation.PluginFunction;
import com.github.winefoxbot.core.model.dto.SessionData;
import com.github.winefoxbot.core.model.enums.Permission;
import com.github.winefoxbot.core.utils.BotUtils;
import com.github.winefoxbot.plugins.imgexploration.model.dto.SearchResultItemDTO;
import com.github.winefoxbot.plugins.imgexploration.service.ImageExplorationRenderer;
import com.github.winefoxbot.plugins.imgexploration.service.ImgExplorationService;
import com.mikuac.shiro.annotation.AnyMessageHandler;
import com.mikuac.shiro.annotation.MessageHandlerFilter;
import com.mikuac.shiro.annotation.common.Shiro;
import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.common.utils.ShiroUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.action.common.ActionData;
import com.mikuac.shiro.dto.action.response.MsgResp;
import com.mikuac.shiro.dto.event.message.AnyMessageEvent;
import com.mikuac.shiro.enums.MsgTypeEnum;
import com.mikuac.shiro.enums.ReplyEnum;
import com.mikuac.shiro.model.ArrayMsg;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2026-01-15-17:17
 */
@Plugin(
        name = "实用功能",
        permission = Permission.USER,
        iconPath = "icon/实用工具.png",
        order = 5)
@Shiro
@Component
@Slf4j
@RequiredArgsConstructor
public class ImgExplorationPlugin {
    private final ImgExplorationService imgExplorationService;
    private final ImageExplorationRenderer renderer;
    private final ScheduledExecutorService scheduledExecutorService;

    private final Map<String, SessionData<List<SearchResultItemDTO>>> sessionCache = new ConcurrentHashMap<>();
    // 匹配纯数字的正则
    private static final Pattern NUMBER_PATTERN = Pattern.compile("^\\d+$");

    /**
     * 初始化定时清理任务
     */
    @PostConstruct
    public void init() {
        // 每 10 秒执行一次清理扫描
        scheduledExecutorService.scheduleAtFixedRate(() -> {
            try {
                if (sessionCache.isEmpty()) {
                    return;
                }
                sessionCache.entrySet().removeIf(entry -> {
                    boolean expired = entry.getValue().isExpired();
                    if (expired) {
                         log.debug("会话 {} 已超时自动清理", entry.getKey());
                    }
                    return expired;
                });
            } catch (Exception e) {
                log.error("清理搜图会话缓存时发生异常", e);
            }
        }, 10, 10, TimeUnit.SECONDS);
    }


    @PluginFunction(
            name = "图片来源搜索",
            description = "/搜图 + (回复一张图片)\n示例：发送一张图片并回复附带文字“/搜图”即可使用此功能, 通过上传的图片搜索其来源，返回可能的出处图片")
    @Async
    @AnyMessageHandler
    @MessageHandlerFilter(types = {MsgTypeEnum.reply, MsgTypeEnum.text}, reply = ReplyEnum.REPLY_ALL, cmd = "\\[CQ:reply,id=\\d+\\](/搜图)$")
    public void searchImage(Bot bot, AnyMessageEvent event) {
        Optional<ArrayMsg> replyMsg = event.getArrayMsg().stream().filter(e -> e.getType() == MsgTypeEnum.reply).findFirst();
        if (replyMsg.isEmpty()) {
            bot.sendMsg(event, "无法找到回复内容~", false);
            return;
        }
        ArrayMsg arrayMsg = replyMsg.get();
        int id = arrayMsg.getData().get("id").asInt();
        ActionData<MsgResp> msg = bot.getMsg(id);
        Integer retCode = msg.getRetCode();
        if (retCode == null || retCode != 0) {
            bot.sendMsg(event, "获取回复消息失败，消息可能已经过期了，你需要重新发下。", false);
            return;
        }
        MsgResp data = msg.getData();
        List<String> msgImgUrls = ShiroUtils.getMsgImgUrlList(data.getArrayMsg());
        if (msgImgUrls.isEmpty()) {
            bot.sendMsg(event, "请回复一张图片以进行搜图~", false);
            return;
        }
        if (msgImgUrls.size() != 1) {
            bot.sendMsg(event, "一次只能搜一张图片哦~", false);
            return;
        }
        String imageUrl = msgImgUrls.getFirst();
        log.info("收到图片搜图请求，图片URL: {}", imageUrl);
        bot.sendMsg(event,"正在搜索图片来源，请稍候...", false);

        imgExplorationService.explore(imageUrl).thenAccept(result -> {
            if (result.items().isEmpty()) {
                bot.sendMsg(event, "未找到相关图片来源，请尝试更换图片或稍后重试。", false);
                return;
            }

            // 1. 渲染并发送图片
            byte[] image = renderer.renderExplorationResult(result.items());
            bot.sendMsg(event, MsgUtils.builder().img(image).text("\n发送对应序号可获取链接 (60s内有效)，发送 0 则退出").build(), false);

            // 2. 存入会话缓存
            String key = BotUtils.getSessionIdWithPrefix(event);
            sessionCache.put(key, new SessionData<>(result.items()));

        }).exceptionally(ex -> {
            log.error("搜图任务失败", ex);
            bot.sendMsg(event, "搜图失败，请稍后重试。", false);
            return null;
        });
    }

    /**
     * 监听数字输入，处理后续链接获取
     */
    @AnyMessageHandler
    @MessageHandlerFilter(types = {MsgTypeEnum.text})
    public void handleIndexInput(Bot bot, AnyMessageEvent event) {
        String msg = event.getMessage().trim();

        // 快速过滤：必须是纯数字
        if (!NUMBER_PATTERN.matcher(msg).matches()) {
            return;
        }

        String key = BotUtils.getSessionIdWithPrefix(event);
        // 检查是否存在活跃会话
        if (!sessionCache.containsKey(key)) {
            return;
        }

        SessionData<List<SearchResultItemDTO>> session = sessionCache.get(key);

        // 二次检查过期 (防止定时任务还没跑)
        if (session.isExpired()) {
            sessionCache.remove(key);
            bot.sendMsg(event, "会话已过期，请重新搜图。", false);
            return;
        }

        // 刷新活跃时间，允许用户继续查询其他序号
        session.refresh();

        try {
            int index = Integer.parseInt(msg);
            // 如果index = 0，则退出
            if (index == 0) {
                sessionCache.remove(key);
                bot.sendMsg(event, "已退出当前搜图会话。", false);
                return;
            }

            // 列表通常是 0-based，但序号通常是 1-based，这里假设图片上的序号从1开始
            int listIndex = index - 1;

            List<SearchResultItemDTO> items = session.getData();
            if (listIndex >= 0 && listIndex < items.size()) {
                SearchResultItemDTO item = items.get(listIndex);

                String reply = "序号 [%d] 的来源:\n标题: %s\n链接: %s".formatted(index, item.title(), item.url());
                // 如果有来源信息，也可以加上
                if (item.source() != null) {
                    reply += "\n来源: " + item.source();
                }

                bot.sendMsg(event, reply, false);
            } else {
                bot.sendMsg(event, "序号 %d 超出范围 (1-%d)".formatted(index, items.size()), false);
            }
        } catch (NumberFormatException e) {
            // ignore
        }
    }
}