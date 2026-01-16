package com.github.winefoxbot.plugins.bittorrent;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-10-23:56
 */

import cn.hutool.core.lang.Pair;
import com.github.winefoxbot.core.annotation.Plugin;
import com.github.winefoxbot.core.annotation.PluginFunction;
import com.github.winefoxbot.core.model.enums.Permission;
import com.github.winefoxbot.core.service.shiro.ShiroSessionStateService;
import com.github.winefoxbot.plugins.bittorrent.config.BitTorrentConfig;
import com.github.winefoxbot.plugins.bittorrent.model.dto.BitTorrentSearchResult;
import com.github.winefoxbot.plugins.bittorrent.model.dto.BitTorrentSearchResultItem;
import com.github.winefoxbot.plugins.bittorrent.service.BitTorrentService;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mikuac.shiro.annotation.AnyMessageHandler;
import com.mikuac.shiro.annotation.MessageHandlerFilter;
import com.mikuac.shiro.annotation.common.Order;
import com.mikuac.shiro.annotation.common.Shiro;
import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.common.utils.ShiroUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.action.common.ActionData;
import com.mikuac.shiro.dto.action.common.MsgId;
import com.mikuac.shiro.dto.event.message.AnyMessageEvent;
import com.mikuac.shiro.enums.MsgTypeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.internal.StringUtil;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;

import static com.github.winefoxbot.core.config.app.WineFoxBotConfig.*;
import static com.github.winefoxbot.core.utils.FileUtil.formatDataSize;
import static com.mikuac.shiro.core.BotPlugin.MESSAGE_BLOCK;
import static com.mikuac.shiro.core.BotPlugin.MESSAGE_IGNORE;

/**
 * BitTorrent 搜索插件
 * 使用 /bt <关键词> [页码] 命令进行搜索
 * 示例命令：
 * /bt IPX
 * /bt IPX 47
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
public class BitTorrentPlugin {

    private final BitTorrentService bitTorrentService;
    private final BitTorrentConfig bitTorrentConfig;
    private final Cache<String, Pair<String, Integer>> nextPageCache = CacheBuilder.newBuilder()
            // 设置在最后一次写入后 1 分钟过期
            .expireAfterWrite(1, TimeUnit.MINUTES)
            // 设置缓存的最大容量，防止内存无限增长
            .maximumSize(50)
            .build();
    private final ShiroSessionStateService sessionStateService;

    @AnyMessageHandler
    @Order(1)
    @MessageHandlerFilter(types = MsgTypeEnum.text)
    public int nextPage(Bot bot, AnyMessageEvent event) {
        String message = event.getMessage();
        String mapKey = sessionStateService.getSessionKey(event);

        if (!"下一页".equals(message)) {
            // ifPresent: 如果存在才执行，避免了 containsKey + get 的两次查找
            if (nextPageCache.getIfPresent(mapKey) != null) {
                // 删除翻页映射，退出命令模式
                nextPageCache.invalidate(mapKey);
                sessionStateService.exitCommandMode(mapKey);
                bot.sendMsg(event, MsgUtils.builder().text("已退出搜索模式").build(), false);
            }
            return MESSAGE_IGNORE;
        }

        String userId = String.valueOf(event.getUserId());
        Integer messageId = event.getMessageId();

        // 使用 getIfPresent 获取，如果 key 不存在或已过期，返回 null
        Pair<String, Integer> nextPageInfo = nextPageCache.getIfPresent(mapKey);

        if (nextPageInfo == null) {
            // 这里可以给用户一个更友好的提示，比如：“当前没有可翻页的内哦，请先进行搜索。”
            return MESSAGE_IGNORE;
        }

        String keyword = nextPageInfo.getKey();
        Integer page = nextPageInfo.getValue();

        // 在 executeSearch 中，当你更新页码时，也要用 put 方法更新缓存
        // nextPageCache.put(mapKey, new Pair<>(keyword, newPage));
        executeSearch(bot, event, messageId, keyword, page, userId);

        return MESSAGE_BLOCK;
    }

    @PluginFunction(name = "磁力链搜索", description = "使用 /bt <关键词> [页码] 命令进行搜索，页码可以不要，不要就默认搜索第一页", commands = {COMMAND_PREFIX + "bt <关键词> [页码]" + COMMAND_SUFFIX})
    @AnyMessageHandler
    @Async
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd =  COMMAND_PREFIX_REGEX + "bt\\s+(\\S+)(\\s+(\\d+))?" + COMMAND_SUFFIX_REGEX)
    public void searchBt(Bot bot, AnyMessageEvent event, Matcher matcher) {
        if (!bitTorrentConfig.isEnabled()) {
            bot.sendMsg(event, MsgUtils.builder().text("BitTorrent 搜索功能已被禁用").build(), false);
            return;
        }
        String keyword = matcher.group(1);
        Integer messageId = event.getMessageId();
        String mapKey = sessionStateService.getSessionKey(event);
        log.info(mapKey);
        int page = 1; // 默认第一页

        if (matcher.group(3) != null) {
            try {
                page = Integer.parseInt(matcher.group(3));
                if (page < 0) {
                    bot.sendMsg(event, MsgUtils.builder().text("页码不能为负数，将使用第一页").build(), false);
                    page = 1;
                }
            } catch (NumberFormatException e) {
                bot.sendMsg(event, MsgUtils.builder().text("页码格式错误，将使用第一页").build(), false);
                page = 1;
            }
        }
        executeSearch(bot, event, messageId, keyword, page,mapKey);
    }

    private void executeSearch(Bot bot, AnyMessageEvent event, Integer messageId, String keyword, int page,  String mapKey) {
        bot.sendMsg(event, MsgUtils.builder()
                .reply(messageId)
                .text("正在进行磁力搜索 \"" + keyword + "\" ，请稍候...")
                .build(), false);

        BitTorrentSearchResult result = null;
        try {
            result = bitTorrentService.search(keyword, page);
            if (result == null) {
                bot.sendMsg(event, MsgUtils.builder().text("未找到相关资源").build(), false);
                // 搜索不到资源，确保退出命令模式
                sessionStateService.exitCommandMode(mapKey);
                return;
            }
            List<BitTorrentSearchResultItem> items = result.getItems();

            if (items.isEmpty()) {
                bot.sendMsg(event, MsgUtils.builder().text("未找到相关资源").build(), false);
                // 搜索不到资源，确保退出命令模式
                sessionStateService.exitCommandMode(mapKey);
                return;
            }

            List<String> msgList = new ArrayList<>();
            for (BitTorrentSearchResultItem item : items) {
                StringBuilder sb = new StringBuilder();
                sb.append("标题：").append(item.getTitle()).append("\n")
                        .append("热度：").append(item.getHot()).append("\n")
                        .append("大小：").append(formatDataSize(item.getSize())).append("\n")
                        .append("日期：").append(item.getDate().toLocalDate()).append("\n")
                        .append("磁力链接：").append(item.getMagnetInfo().getMagnet()).append("\n")
                        .append("文件列表：").append(StringUtil.join(item.getMagnetInfo().getFiles().stream().map(e -> "\n- \"" + e + "\"").toList(), ""));
                msgList.add(sb.toString());
            }

            ActionData<MsgId> msgIdActionData = bot.sendForwardMsg(event, ShiroUtils.generateForwardMsg(bot.getSelfId(), "bitTorrent", msgList));
            StringBuilder sb = new StringBuilder();
            if (result.getPageInfo() != null && result.getPageInfo().isHasNextPage()) {
                sb.append("在60秒内输入\"下一页\"可以查询下一页内容，输入其它任意消息则退出搜索");
                // **关键：有下一页，进入命令模式**
                sessionStateService.enterCommandMode(mapKey);
            } else {
                sb.append("搜索完成，已无更多结果");
                // **关键：没有下一页了，确保退出命令模式**
                sessionStateService.exitCommandMode(mapKey);
            }
            bot.sendMsg(event, MsgUtils.builder()
                    .reply(msgIdActionData.getData().getMessageId())
                    .text(sb.toString())
                    .build(), false);
            // 自动撤回
            if (bitTorrentConfig.isAutoRevoke()) {
                // 撤回消息
                Integer sentMessageId = msgIdActionData.getData().getMessageId();
                revokeMessage(bot, sentMessageId);
            }
        } catch (NullPointerException e) {
            log.error("搜索结果为空", e);
            bot.sendMsg(event, MsgUtils.builder()
                    .reply(messageId)
                    .text("搜索失败，请重试").build(), false);
            sessionStateService.exitCommandMode(mapKey); // 异常时也要退出
        } catch (Exception e) {
            log.error("搜索失败", e);
            bot.sendMsg(event, MsgUtils.builder()
                    .reply(messageId)
                    .text("搜索出错：" + e.getMessage()).build(), false);
            sessionStateService.exitCommandMode(mapKey); // 异常时也要退出
        } finally {
            if (result != null && result.getPageInfo() != null && result.getPageInfo().isHasNextPage()) {
                // 如果有下一页，就更新缓存中的页码信息。
                // 这会重置该 key 的过期计时器（无论是 expireAfterWrite 还是 expireAfterAccess）。
                nextPageCache.put(mapKey, Pair.of(keyword, page + 1));
            } else {
                // 如果没有下一页了，或者搜索失败 (result 为 null)，
                // 应该显式地让这个 key 失效，立即释放资源并退出翻页模式。
                nextPageCache.invalidate(mapKey);
                // 最好也在这里显式退出命令模式，确保状态一致性
                sessionStateService.exitCommandMode(mapKey);
            }
        }
    }

    private void revokeMessage(Bot bot, Integer messageId) {
        try {
            TimeUnit.SECONDS.sleep(bitTorrentConfig.getRevokeDelaySeconds());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            bot.deleteMsg(messageId);
        }
    }

}