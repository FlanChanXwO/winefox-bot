package com.github.winefoxbot.plugins;

import com.github.winefoxbot.annotation.PluginFunction;
import com.mikuac.shiro.annotation.GroupMessageHandler;
import com.mikuac.shiro.annotation.MessageHandlerFilter;
import com.mikuac.shiro.annotation.common.Order;
import com.mikuac.shiro.annotation.common.Shiro;
import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import com.mikuac.shiro.enums.MsgTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 复读机插件
 *
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-09-2:12
 */
@Shiro
@Slf4j
@Component
public class RepeaterPlugin{


    // 用于存储开启了复读跟随功能的用户
// Key: groupId, Value: Set of userIds
    private final Map<Long, Set<Long>> repeaterFollowers = new ConcurrentHashMap<>();

    // 每个群组的最大跟随人数限制
    private static final int MAX_FOLLOWERS_PER_GROUP = 10;


    // 开启复读跟随
    @PluginFunction(group = "复读机", name = "复读跟随", description = "使用 /复读跟随 命令开启复读跟随功能，使用 /停止复读跟随 关闭该功能。当你发送消息时，机器人会自动复读你的消息。", commands = {"/复读跟随", "/停止复读跟随"})
    @GroupMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = "^/复读跟随$")
    public void enableFollowRepeat(Bot bot, GroupMessageEvent event) {
        Long userId = event.getSender().getUserId();
        Long groupId = event.getGroupId();

        // 1. 获取或创建当前群组的跟随者集合
        // computeIfAbsent 确保了线程安全地创建 Set
        Set<Long> followersInGroup = repeaterFollowers.computeIfAbsent(groupId, k -> ConcurrentHashMap.newKeySet());

        // 2. 检查是否已达到人数上限
        // 在添加前检查，需要考虑当前用户是否已经存在于集合中
        if (followersInGroup.size() >= MAX_FOLLOWERS_PER_GROUP && !followersInGroup.contains(userId)) {
            log.warn("Group {} reached max repeater followers limit. User {} failed to enable.", groupId, userId);
            bot.sendGroupMsg(groupId, MsgUtils.builder()
                    .at(userId)
                    .text("抱歉，本群的复读跟随名额（" + MAX_FOLLOWERS_PER_GROUP + "人）已满！")
                    .build(), false);
            return;
        }

        // 3. 将用户添加到集合中
        // Set.add() 方法如果用户已存在，会返回 false，否则返回 true
        if (followersInGroup.add(userId)) {
            // 如果添加成功（之前不在集合里）
            log.info("User {} enabled repeater follow in group {}. Current followers: {}", userId, groupId, followersInGroup.size());
            bot.sendGroupMsg(groupId, MsgUtils.builder()
                    .at(userId)
                    .text("已为你开启复读跟随功能！现在我会复读你发送的所有消息啦~")
                    .build(), false);
        } else {
            // 如果添加失败（说明已经开启了）
            log.info("User {} tried to enable repeater follow again in group {}.", userId, groupId);
            bot.sendGroupMsg(groupId, MsgUtils.builder()
                    .at(userId)
                    .text("你已经开启了复读跟随功能，无需重复操作。")
                    .build(), false);
        }
    }

    // 关闭复读跟随
    @PluginFunction(group = "复读机", name = "/", description = "使用 /停止复读跟随 命令关闭复读跟随功能。", commands = {"/停止复读跟随"})
    @GroupMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = "^/(停止|关闭|取消)复读跟随$")
    public void disableFollowRepeat(Bot bot, GroupMessageEvent event) {
        Long userId = event.getSender().getUserId();
        Long groupId = event.getGroupId();

        // 1. 获取当前群组的跟随者集合
        Set<Long> followersInGroup = repeaterFollowers.get(groupId);

        // 2. 检查并移除用户
        if (followersInGroup != null && followersInGroup.remove(userId)) {
            // 如果移除成功
            log.info("User {} disabled repeater follow in group {}. Current followers: {}", userId, groupId, followersInGroup.size());
            bot.sendGroupMsg(groupId, MsgUtils.builder()
                    .at(userId)
                    .text("已关闭复读跟随功能。")
                    .build(), false);
        } else {
            // 如果集合不存在，或用户不在集合中
            log.info("User {} tried to disable non-existent repeater follow in group {}.", userId, groupId);
            bot.sendGroupMsg(groupId, MsgUtils.builder()
                    .at(userId)
                    .text("你当前未开启复读跟随功能。")
                    .build(), false);
        }
    }


    @Order(100)
    @GroupMessageHandler
    @Async
    public void handleFollowerMessage(Bot bot, GroupMessageEvent event) {
        Long userId = event.getSender().getUserId();
        Long groupId = event.getGroupId();
        String message = event.getMessage();

        Set<Long> followersInGroup = repeaterFollowers.get(groupId);

        if (followersInGroup != null && followersInGroup.contains(userId)) {
            log.info("Following and repeating message from user {} in group {}", userId, groupId);
            bot.sendGroupMsg(groupId, message, false);
        }
    }



    private static final int SHORTEST_LENGTH = 1; // 最短消息长度
    private static final int SHORTEST_TIMES = 4;  // 最少重复次数
    private static final String[] BLACKLIST = {"黑名单消息1", "黑名单消息2"}; // 黑名单消息

    private final Map<Long, String> lastMessage = new ConcurrentHashMap<>();
    private final Map<Long, Integer> messageTimes = new ConcurrentHashMap<>();
    private final Map<Long, Long> lastSenderId = new ConcurrentHashMap<>();

    private static final Pattern IMAGE_PATTERN = Pattern.compile("\\[CQ:image.*?file=(.*?).jpg.*?url=(.*?)\\]");

    @Order(100)
    @GroupMessageHandler
    public void handleRepeatPlusOneMessage(Bot bot, GroupMessageEvent event) {
        // --- 原有逻辑开始 ---
        if (event.getGroupId() == null) {
            return;
        }

        String rawMessage = event.getMessage();
        Long currentSenderId = event.getSender().getUserId();
        long groupId = event.getGroupId();

        // 对于命令消息，直接忽略
        if (rawMessage.startsWith("/")) {
            // 在忽略前，重置计数器，防止 "/命令" 中断计数后，下一条相同消息继续计数
            resetCounter(groupId);
            return;
        }

        // 黑名单检查
        for (String blocked : BLACKLIST) {
            if (rawMessage.equals(blocked)) {
                resetCounter(groupId); // 中断计数
                return;
            }
        }

        // 忽略过短消息
        if (rawMessage.length() < SHORTEST_LENGTH) {
            resetCounter(groupId); // 中断计数
            return;
        }

        // 消息预处理，统一图片CQ码，便于比较
        String processedMessage = preprocessMessage(rawMessage);

        String prevMessage = lastMessage.get(groupId);
        Long prevSenderId = lastSenderId.get(groupId);

        // 核心逻辑修改：判断消息是否与上一条相同
        if (processedMessage.equals(prevMessage)) {
            // **新增规则：当前发送者必须与上一个发送者不同**
            if (prevSenderId != null && !currentSenderId.equals(prevSenderId)) {
                // 消息相同，且不是同一个人发的，计数器+1
                int newTimes = messageTimes.getOrDefault(groupId, 1) + 1;
                messageTimes.put(groupId, newTimes);

                // 判断是否达到触发复读的次数
                if (newTimes == SHORTEST_TIMES) {
                    // log.info("RepeaterPlugin triggered in group {}: message '{}'", groupId, rawMessage);
                    bot.sendGroupMsg(groupId, rawMessage, false); // 发送原始消息

                    // 触发复读后，立即重置计数器，避免刷屏
                    resetCounter(groupId);
                    return; // 结束本次处理，防止更新 lastSenderId 和 lastMessage
                }
            }
            // 如果是同一个人连续发送相同消息，则不增加计数，但也不重置，等待其他人加入复读
        } else {
            // 消息与上一条不同，重置计数器，并记录新消息为“第一条”
            messageTimes.put(groupId, 1);
        }

        // 无论是否触发复读，都更新最后一条消息的内容和发送者
        lastMessage.put(groupId, processedMessage);
        lastSenderId.put(groupId, currentSenderId);
    }


    /**
     * 重置指定群组的复读计数器和状态。
     *
     * @param groupId 群号
     */
    private void resetCounter(long groupId) {
        lastMessage.remove(groupId);
        messageTimes.remove(groupId);
        lastSenderId.remove(groupId);
    }

    /**
     * 消息预处理，把图片 CQ 码统一成 [file_id]，用于对比重复消息。
     * 这样可以确保内容相同的多张图片被视为同一消息。
     */
    private String preprocessMessage(String message) {
        Matcher matcher = IMAGE_PATTERN.matcher(message);
        // 使用 StringBuffer/StringBuilder 以获得更好的性能
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            // group(1) 捕获的是 file ID
            String fileId = matcher.group(1);
            // 将整个 CQ 码替换为一个统一的、可识别的占位符
            matcher.appendReplacement(sb, "[image_id:" + fileId + "]");
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}