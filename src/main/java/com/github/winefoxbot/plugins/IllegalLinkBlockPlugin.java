package com.github.winefoxbot.plugins;

import cn.hutool.core.collection.ConcurrentHashSet;
import com.github.winefoxbot.config.WineFoxBotConfig;
import com.github.winefoxbot.service.core.DomainAllowListService;
import com.mikuac.shiro.annotation.GroupMessageHandler;
import com.mikuac.shiro.annotation.MessageHandlerFilter;
import com.mikuac.shiro.annotation.common.Shiro;
import com.mikuac.shiro.common.utils.ShiroUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.action.common.ActionRaw;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import com.mikuac.shiro.enums.MsgTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 非法链接拦截器 (带有渐进式惩罚和冷却重置)
 *
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-14-19:44
 */
@Shiro
@Component
@Slf4j
@RequiredArgsConstructor
public class IllegalLinkBlockPlugin {

    private final WineFoxBotConfig wineFoxBotConfig;

    // 定义违规次数重置的冷却时间（单位：分钟）
    private static final long VIOLATION_RESET_MINUTES = 5;

    private static final Pattern URL_EXTRACTION_PATTERN = Pattern.compile(
            // 匹配带协议的URL (http/https/ftp) 或 IP地址链接
            "(?:https?|ftp)://(?:(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,63}|(?:\\d{1,3}\\.){3}\\d{1,3})(?::\\d+)?(?:/[^\\s]*)?" +
                    "|" + // 或者
                    // 匹配以 www, pan, bbs 等开头的、省略了协议的常见链接
                    "\\b(?:www|pan|bbs)\\.[a-zA-Z0-9][a-zA-Z0-9-]{0,62}\\.[a-zA-Z]{2,63}(?:/[^\\s]*)?"
    );


    // Key: 用户QQ号 (Long), Value: 违规记录对象 (ViolationRecord)
    private final Map<Long, ViolationRecord> violationTracker = new ConcurrentHashMap<>();

    private final DomainAllowListService domainAllowListService;

    private final Set<Long> enableIllegalLinkBlockGroups = new ConcurrentHashSet<>();

    @GroupMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = "^/启用链接拦截$")
    public void enableIllegalLinkBlockInGroup(Bot bot, GroupMessageEvent event) {
        Long userId = event.getSender().getUserId();
        List<Long> adminUsers = wineFoxBotConfig.getAdminUsers();
        if (!adminUsers.isEmpty() && !adminUsers.contains(userId)) {
            return; // 仅允许管理员启用该功能
        }

        Long groupId = event.getGroupId();
        if (enableIllegalLinkBlockGroups.add(groupId)) {
            bot.sendGroupMsg(groupId, "已启用链接拦截功能。", false);
            log.info("群组[{}] 已启用链接拦截功能。", groupId);
        } else {
            bot.sendGroupMsg(groupId, "链接拦截功能已启用，无需重复操作。", false);
            log.info("群组[{}] 链接拦截功能已启用，忽略重复启用请求。", groupId);
        }
    }

    @GroupMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = "^/禁用链接拦截$")
    public void disableIllegalLinkBlockInGroup(Bot bot, GroupMessageEvent event) {
        Long userId = event.getSender().getUserId();
        List<Long> adminUsers = wineFoxBotConfig.getAdminUsers();
        if (!adminUsers.isEmpty() && !adminUsers.contains(userId)) {
            return; // 仅允许管理员启用该功能
        }

        Long groupId = event.getGroupId();
        if (enableIllegalLinkBlockGroups.remove(groupId)) {
            bot.sendGroupMsg(groupId, "已禁用链接拦截功能。", false);
            log.info("群组[{}] 已禁用链接拦截功能。", groupId);
        } else {
            bot.sendGroupMsg(groupId, "链接拦截功能未启用，无需重复操作。", false);
            log.info("群组[{}] 链接拦截功能未启用，忽略重复禁用请求。", groupId);
        }
    }


    @GroupMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text)
    public void handleIllegalLink(Bot bot, GroupMessageEvent event) {
        if (!enableIllegalLinkBlockGroups.contains(event.getGroupId())) {
            return; // 如果该群组未启用链接拦截功能，直接返回
        }
        String message = event.getRawMessage();
        List<String> msgImgUrlList = ShiroUtils.getMsgImgUrlList(event.getArrayMsg());
        List<String> msgVideoUrlList = ShiroUtils.getMsgVideoUrlList(event.getArrayMsg());
        Long groupId = event.getGroupId();
        Long userId = event.getSender().getUserId();
        String userNickname = event.getSender().getNickname();

        if (msgImgUrlList.contains(message) || msgVideoUrlList.contains(message)) {
            log.debug("消息仅包含图片或视频链接，跳过链接检查。");
            return;
        }

        log.info("群组[{}] 用户[{}({})] 发送消息: {}", groupId, userNickname, userId, message);

        Matcher matcher = URL_EXTRACTION_PATTERN.matcher(message);

        while (matcher.find()) {
            String urlString = matcher.group();
            try {
                String host = getHostFromUrlString(urlString);
                if (host.isEmpty()) {
                    continue;
                }

                if (!domainAllowListService.isDomainAllowed(host)) {
                    log.warn("群组[{}] 用户[{}({})] 发送了违规链接: {}", groupId, userNickname, userId, urlString);
                    processViolation(bot, event, host);
                    return;
                } else {
                    log.debug("域名 '{}' 在白名单内，继续检查下一个 URL...", host);
                }
            } catch (URISyntaxException e) {
                log.warn("无法将 '{}' 解析为有效的 URI，已跳过。错误: {}", urlString, e.getMessage());
            }
        }
    }

    /**
     * 处理违规行为，计算惩罚等级并执行
     */
    private void processViolation(Bot bot, GroupMessageEvent event, String illegalHost) {
        Long userId = event.getSender().getUserId();
        Instant now = Instant.now();

        // 获取用户过去的记录，如果不存在，则创建一个全新的、空的记录（次数为0，时间为纪元初）
        ViolationRecord record = violationTracker.getOrDefault(userId, new ViolationRecord(0, Instant.EPOCH));

        int newViolationCount;

        // --- 核心判断逻辑 ---
        // 检查当前违规与上次违规的时间差是否超过了设定的冷却时间
        if (Duration.between(record.getLastViolationTimestamp(), now).toMinutes() >= VIOLATION_RESET_MINUTES) {
            // 如果超过了冷却时间（或者这是第一次违规，因为Instant.EPOCH会使这个条件成立）
            // 将本次违规视为全新的“第一次”
            newViolationCount = 1;
            log.info("用户 {} 的违规记录已超过 {} 分钟冷却期，重置计数为 1。", userId, VIOLATION_RESET_MINUTES);
        } else {
            // 如果仍在冷却时间内，累加违规次数
            newViolationCount = record.getCount() + 1;
            log.info("用户 {} 在 {} 分钟冷却期内再次违规，计数增加到 {}。", userId, VIOLATION_RESET_MINUTES, newViolationCount);
        }

        // 更新或创建用户的违规记录
        record.setCount(newViolationCount);
        record.setLastViolationTimestamp(now);
        violationTracker.put(userId, record);

        // --- 根据新的违规次数决定惩罚措施 ---
        int muteDurationMinus = 0;
        String reasonMessage = null;
        boolean notifyAdmin = false;
        boolean muted = false;

        if (newViolationCount >= 3) {
            muteDurationMinus = 1; // 第三次及以上，禁言1分钟
            reasonMessage = String.format("用户 %s(%d) 在%d分钟内第 %d 次发送违规链接，禁言%d分钟并通知管理员！", event.getSender().getNickname(), userId, VIOLATION_RESET_MINUTES, newViolationCount, muteDurationMinus);
            notifyAdmin = true;
            muted = true;
        }

        log.info("执行惩罚: {}", reasonMessage);

        // 1. 撤回违规消息
        bot.deleteMsg(event.getMessageId());

        // 2. 执行禁言
        if (muted) {
            ActionRaw banResult = bot.setGroupBan(event.getGroupId(), userId, muteDurationMinus);
            if ("failed".equals(banResult.getStatus())) {
                log.error("禁言操作失败: {}", banResult);
                bot.sendGroupMsg(event.getGroupId(), "无法禁言用户 " + event.getSender().getNickname() + "，请检查我的权限设置。", false);
                // 3. 在群里发送通知
                bot.sendGroupMsg(event.getGroupId(), reasonMessage, false);
            }
        }

        // 3. 如果需要，通知管理员
        if (notifyAdmin) {
            List<Long> adminUsers = wineFoxBotConfig.getAdminUsers();
            if (!adminUsers.isEmpty()) {
                for (Long adminUser : adminUsers) {
                    String adminNotification = String.format(
                            "管理员请注意！\n群组: %d (%s)\n用户: %s(%d)\n多次发送违规链接(%s)，已被自动禁言%d分钟。",
                            event.getGroupId(),
                            bot.getGroupInfo(event.getGroupId(), true).getData().getGroupName(), // 尝试获取群名，增加可读性
                            event.getSender().getNickname(),
                            userId,
                            illegalHost,
                            muteDurationMinus
                    );
                    bot.sendPrivateMsg(adminUser, adminNotification, false);
                }
            }

        }
    }

    private String getHostFromUrlString(String urlString) throws URISyntaxException {
        String processedUrlString = urlString;
        if (!processedUrlString.matches("^[a-zA-Z]+://.*")) {
            processedUrlString = "http://" + processedUrlString;
        }
        URI uri = new URI(processedUrlString);
        String host = uri.getHost();
        return (host != null) ? host : "";
    }

    @Data
    @AllArgsConstructor
    private static class ViolationRecord {
        private int count;
        private Instant lastViolationTimestamp;
    }
}
