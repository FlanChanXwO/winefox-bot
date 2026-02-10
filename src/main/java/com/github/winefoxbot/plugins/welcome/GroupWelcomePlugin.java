package com.github.winefoxbot.plugins.welcome;

import com.github.winefoxbot.core.annotation.plugin.Plugin;
import com.github.winefoxbot.core.model.dto.TextReply;
import com.github.winefoxbot.core.model.dto.TextReplyParams;
import com.github.winefoxbot.core.model.enums.common.GroupAdminChangeType;
import com.github.winefoxbot.core.model.enums.common.GroupMemberDecreaseType;
import com.github.winefoxbot.core.model.enums.reply.BotReplyTemplateType;
import com.github.winefoxbot.core.service.reply.TextReplyService;
import com.github.winefoxbot.core.util.BotUtil;
import com.mikuac.shiro.annotation.GroupAdminHandler;
import com.mikuac.shiro.annotation.GroupDecreaseHandler;
import com.mikuac.shiro.annotation.GroupIncreaseHandler;
import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.notice.GroupAdminNoticeEvent;
import com.mikuac.shiro.dto.event.notice.GroupDecreaseNoticeEvent;
import com.mikuac.shiro.dto.event.notice.GroupIncreaseNoticeEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static com.github.winefoxbot.core.model.enums.common.PluginType.PASSIVE;

/**
 * 群欢迎插件 (被动插件)
 * <p>
 * 专注于处理群内的交互反馈，如欢迎新人、成员离开提醒、管理员变动提示等。
 * </p>
 *
 * @author FlanChan
 */
@Plugin(
        name = "群事件通知",
        description = "提供群成员变动欢迎、离开提示以及管理员变更提示等功能。",
        type = PASSIVE, // 标记为被动插件
        builtIn = true,
        order = 100
)
@Slf4j
@RequiredArgsConstructor
public class GroupWelcomePlugin {

    private final TextReplyService textReplyService;

    /**
     * 群成员增加 - 发送欢迎语
     */
    @GroupIncreaseHandler
    public void handleGroupIncrease(Bot bot, GroupIncreaseNoticeEvent event) {
        Long groupId = event.getGroupId();
        Long userId = event.getUserId();
        Long botId = bot.getSelfId();

        // 如果是Bot自己进群，通常不自己欢迎自己，或者逻辑不同，这里暂且跳过
        if (userId.equals(botId)) {
            return;
        }

        log.info("插件检测到群成员 {} 加入群 {}，准备发送欢迎语", userId, groupId);
        String username = BotUtil.getGroupMemberNickname(bot, groupId, userId);
        
        TextReply reply = textReplyService.getReply(new TextReplyParams(username, BotReplyTemplateType.WELCOME));
        sendReply(bot, reply, groupId, userId, true);
    }

    /**
     * 群成员减少 - 发送送别/提示语
     */
    @GroupDecreaseHandler
    public void handleGroupDecrease(Bot bot, GroupDecreaseNoticeEvent event) {
        Long groupId = event.getGroupId();
        Long userId = event.getUserId();

        String username = BotUtil.getStrangeNickname(bot, userId);
        GroupMemberDecreaseType type = GroupMemberDecreaseType.fromValue(event.getSubType());
        
        TextReply reply = null;
        switch (type) {
            case KICK -> {
                log.info("群成员 {} 被踢出，插件发送提示", userId);
                reply = textReplyService.getReply(new TextReplyParams(username, BotReplyTemplateType.KICK));
            }
            case LEAVE -> {
                log.info("群成员 {} 离开，插件发送送别", userId);
                reply = textReplyService.getReply(new TextReplyParams(username, BotReplyTemplateType.FAREWELL));
            }
            // KICK_ME 的情况通常不需要发送消息，或者由 Shiro 框架底层处理日志
        }
        
        // 发送消息，不At离群的人（因为已经不在群里了，At也没用或者发不出去）
        sendReply(bot, reply, groupId, null, false);
    }

    /**
     * 管理员变动 - 发送提示
     */
    @GroupAdminHandler
    public void handleGroupAdmin(Bot bot, GroupAdminNoticeEvent event) {
        Long groupId = event.getGroupId();
        Long userId = event.getUserId();
        Long botId = bot.getSelfId();
        GroupAdminChangeType type = GroupAdminChangeType.fromValue(event.getSubType());

        // 目前逻辑只处理Bot自己的变动提示
        if (userId.equals(botId)) {
            TextReply reply = null;
            switch (type) {
                case SET -> {
                    reply = textReplyService.getReply(new TextReplyParams(null, BotReplyTemplateType.PROMOTE));
                }
                case UNSET -> {
                    reply = textReplyService.getReply(new TextReplyParams(null, BotReplyTemplateType.DEMOTE));
                }
            }
            sendReply(bot, reply, groupId, null, false);
        }
    }


    private void sendReply(Bot bot, TextReply reply, Long groupId, Long atUserId, boolean at) {
        if (reply != null) {
            MsgUtils msgBuilder = MsgUtils.builder();
            if (at && atUserId != null) {
                msgBuilder.at(atUserId);
            }
            msgBuilder.text(reply.getText());
            if (reply.getPicture() != null) {
                msgBuilder.img(reply.getPicture());
            }
            bot.sendGroupMsg(groupId, msgBuilder.build(), false);
        }
    }
}