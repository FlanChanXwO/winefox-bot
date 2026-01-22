package com.github.winefoxbot.core.plugins.event;

import com.github.winefoxbot.core.annotation.plugin.Plugin;
import com.github.winefoxbot.core.model.dto.TextReply;
import com.github.winefoxbot.core.model.dto.TextReplyParams;
import com.github.winefoxbot.core.model.enums.reply.BotReplyTemplateType;
import com.github.winefoxbot.core.model.enums.common.GroupAdminChangeType;
import com.github.winefoxbot.core.model.enums.common.GroupMemberDecreaseType;
import com.github.winefoxbot.core.service.reply.TextReplyService;
import com.github.winefoxbot.core.service.shiro.ShiroGroupMembersService;
import com.github.winefoxbot.core.service.shiro.ShiroGroupRequestsService;
import com.github.winefoxbot.core.service.shiro.ShiroGroupsService;
import com.github.winefoxbot.core.service.shiro.ShiroMessagesService;
import com.github.winefoxbot.core.utils.BotUtils;
import com.mikuac.shiro.annotation.*;
import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.notice.*;
import com.mikuac.shiro.dto.event.request.GroupAddRequestEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2026-01-07-18:14
 */
@Plugin(
        name = "群事件监听插件",
        description = "监听和处理各种群事件，如成员增加、成员减少、管理员变更等。然后进行欢迎",
        order = 99)
@Slf4j
@RequiredArgsConstructor
public class GroupEventListenerPlugin {
    private final ShiroMessagesService shiroMessagesService;


    @GroupMsgDeleteNoticeHandler
    public void handleGroupMessageDelete(GroupMsgDeleteNoticeEvent event) {
        Integer messageId = event.getMessageId();
        shiroMessagesService.removeByMessageId(messageId);
    }


    private final TextReplyService textReplyService;
    private final ShiroGroupMembersService shiroGroupMembersService;
    private final ShiroGroupRequestsService shiroGroupRequestsService;
    private final ShiroGroupsService groupsService;

    /**
     * 群成员增加事件处理器
     *
     * @param bot
     * @param event
     */
    @GroupIncreaseHandler
    public void handleGroupIncrease(Bot bot, GroupIncreaseNoticeEvent event) {
        Long groupId = event.getGroupId();
        Long userId = event.getUserId();
        Long botId = bot.getSelfId();
        if (userId.equals(botId)) {
            log.info("Bot {} 被添加到群 {}", botId, groupId);
            return;
        }
        log.info("群成员 {} 加入群 {}", userId, groupId);
        String username = BotUtils.getGroupMemberNickname(bot, groupId, userId);
        // 获取模板
        TextReply reply = textReplyService.getReply(new TextReplyParams(username, BotReplyTemplateType.WELCOME));
        sendReply(bot, reply, event.getGroupId(), userId);
    }

    /**
     * 群成员减少事件处理器
     *
     * @param bot
     * @param event
     */
    @GroupDecreaseHandler
    public void handleGroupDecrease(Bot bot, GroupDecreaseNoticeEvent event) {
        Long groupId = event.getGroupId();
        Long userId = event.getUserId();
        Long operatorId = event.getOperatorId();
        String username = BotUtils.getStrangeNickname(bot, userId);
        GroupMemberDecreaseType groupMemberDecreaseType = GroupMemberDecreaseType.fromValue(event.getSubType());
        TextReply reply = null;
        switch (groupMemberDecreaseType) {
            case GroupMemberDecreaseType.KICK -> {
                log.info("群成员 {} 被管理员 {} 踢出群 {}", userId, operatorId, groupId);
                // 获取模板
                reply = textReplyService.getReply(new TextReplyParams(username, BotReplyTemplateType.KICK));
            }
            case GroupMemberDecreaseType.LEAVE -> {
                log.info("群成员 {} 从群 {} 中离开", userId, groupId);
                // 获取模板
                reply = textReplyService.getReply(new TextReplyParams(username, BotReplyTemplateType.FAREWELL));
            }
            case GroupMemberDecreaseType.KICK_ME -> {
                log.info("群成员 {} 主动将 Bot {} 踢出群 {}", operatorId, bot.getSelfId(), groupId);
                // 删除群信息
                groupsService.deleteGroupInfo(groupId, bot.getSelfId());
            }
        }
        sendReply(bot, reply, event.getGroupId());
        // 删除成员信息
        shiroGroupMembersService.deleteGroupMemberInfo(groupId, userId);
    }

    @GroupAdminHandler
    public void handleGroupAdmin(Bot bot, GroupAdminNoticeEvent event) {
        Long groupId = event.getGroupId();
        Long userId = event.getUserId();
        Long botId = bot.getSelfId();
        GroupAdminChangeType groupAdminChangeType = GroupAdminChangeType.fromValue(event.getSubType());
        TextReply reply = null;
        if (userId.equals(botId)) {
            switch (groupAdminChangeType) {
                case SET -> {
                    log.info("Bot {} 在群 {} 中被提升为管理员", botId, groupId);
                    reply = textReplyService.getReply(new TextReplyParams(null, BotReplyTemplateType.PROMOTE));
                }
                case UNSET -> {
                    log.info("Bot {} 在群 {} 中被降级为普通成员", botId, groupId);
                    reply = textReplyService.getReply(new TextReplyParams(null, BotReplyTemplateType.DEMOTE));
                }
            }
        }
        sendReply(bot, reply, event.getGroupId());
        shiroGroupMembersService.saveOrUpdateGroupMemberInfo(event);
    }



    @GroupCardChangeNoticeHandler
    public void handleGroupCardChange(GroupCardChangeNoticeEvent event) {
        Long groupId = event.getGroupId();
        Long userId = event.getUserId();
        String newCard = event.getCardNew();
        String oldCard = event.getCardOld();
        log.info("群成员 {} 在群 {} 中修改了群名片: 旧名片='{}', 新名片='{}'", userId, groupId, oldCard, newCard);
        // 这里可以添加其他逻辑，例如更新数据库中的群成员信息等
        shiroGroupMembersService.saveOrUpdateGroupMemberInfo(event);
    }

    private void sendReply(Bot bot, TextReply reply, Long groupId, Long userId,  boolean at) {
        if (reply != null) {
            // 构建消息
            MsgUtils msgBuilder = MsgUtils.builder();
            if (at) {
                msgBuilder.at(userId);
            }
            msgBuilder.text(reply.getText());
            if (reply.getPicture() != null) {
                msgBuilder.img(reply.getPicture());
            }
            String message = msgBuilder.build();
            // 发送消息
            bot.sendGroupMsg(groupId, message, false);
        }
    }

    private void sendReply(Bot bot, TextReply reply, Long groupId,Long userId) {
        sendReply(bot, reply, groupId, userId, true);
    }

    private void sendReply(Bot bot, TextReply reply, Long groupId) {
        sendReply(bot, reply, groupId, null, false);
    }

    @GroupAddRequestHandler
    public void handleGroupAddRequest(Bot bot, GroupAddRequestEvent event) {
        log.info("收到群添加请求: {}", event);
        shiroGroupRequestsService.saveGroupAddRequest( event);
    }


    @GroupUploadNoticeHandler
    public void handleGroupFileUpload(Bot bot, GroupUploadNoticeEvent event) {
        //TODO
    }
}