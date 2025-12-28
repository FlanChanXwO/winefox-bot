package com.github.winefoxbot.plugins;

import com.github.winefoxbot.model.dto.reply.BotReply;
import com.github.winefoxbot.model.dto.reply.BotReplyParams;
import com.github.winefoxbot.model.entity.QQGroupAutoHandleAddRequestFeatureConfig;
import com.github.winefoxbot.model.enums.BotReplyTemplateType;
import com.github.winefoxbot.model.enums.GroupAddRequestType;
import com.github.winefoxbot.model.enums.GroupAdminChangeType;
import com.github.winefoxbot.model.enums.GroupMemberDecreaseType;
import com.github.winefoxbot.service.bot.BotReplyService;
import com.github.winefoxbot.service.qqgroup.QQGroupService;
import com.github.winefoxbot.service.shiro.ShiroGroupMembersService;
import com.github.winefoxbot.utils.BotUtils;
import com.mikuac.shiro.annotation.*;
import com.mikuac.shiro.annotation.common.Order;
import com.mikuac.shiro.annotation.common.Shiro;
import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.notice.GroupAdminNoticeEvent;
import com.mikuac.shiro.dto.event.notice.GroupCardChangeNoticeEvent;
import com.mikuac.shiro.dto.event.notice.GroupDecreaseNoticeEvent;
import com.mikuac.shiro.dto.event.notice.GroupIncreaseNoticeEvent;
import com.mikuac.shiro.dto.event.request.GroupAddRequestEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import static com.mikuac.shiro.core.BotPlugin.MESSAGE_BLOCK;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-04-11:57
 */
@Shiro
@Component
@Slf4j
@RequiredArgsConstructor
public class QQGroupPlugin {

    private final BotReplyService botReplyService;
    private final ShiroGroupMembersService shiroGroupMembersService;
    private final QQGroupService qqGroupService;

    /**
     * 群成员增加事件处理器
     *
     * @param bot
     * @param event
     */
    @GroupIncreaseHandler
    @Order(1)
    public int handleGroupIncrease(Bot bot, GroupIncreaseNoticeEvent event) {
        Long groupId = event.getGroupId();
        Long userId = event.getUserId();
        Long botId = bot.getSelfId();
        if (userId.equals(botId)) {
            log.info("Bot {} 被添加到群 {}", botId, groupId);
            return MESSAGE_BLOCK;
        }
        String username = BotUtils.getGroupMemberNickname(bot, groupId, userId);
        // 获取模板
        BotReply reply = botReplyService.getReply(new BotReplyParams(username, BotReplyTemplateType.WELCOME));
        sendReply(bot, reply, event.getGroupId(), userId);
        return MESSAGE_BLOCK;
    }

    /**
     * 群成员减少事件处理器
     *
     * @param bot
     * @param event
     */
    @GroupDecreaseHandler
    @Order(1)
    public int handleGroupDecrease(Bot bot, GroupDecreaseNoticeEvent event) {
        Long groupId = event.getGroupId();
        Long userId = event.getUserId();
        Long operatorId = event.getOperatorId();
        String username = BotUtils.getStrangeNickname(bot, userId);
        GroupMemberDecreaseType groupMemberDecreaseType = GroupMemberDecreaseType.fromValue(event.getSubType());
        BotReply reply = null;
        switch (groupMemberDecreaseType) {
            case GroupMemberDecreaseType.KICK -> {
                log.info("群成员 {} 被管理员 {} 踢出群 {}", userId, operatorId, groupId);
                // 获取模板
                reply = botReplyService.getReply(new BotReplyParams(username, BotReplyTemplateType.KICK));
            }
            case GroupMemberDecreaseType.LEAVE -> {
                log.info("群成员 {} 从群 {} 中离开", userId, groupId);
                // 获取模板
                reply = botReplyService.getReply(new BotReplyParams(username, BotReplyTemplateType.FAREWELL));
            }
            case GroupMemberDecreaseType.KICK_ME -> {
                log.info("群成员 {} 主动将 Bot {} 踢出群 {}", operatorId, bot.getSelfId(), groupId);
            }
        }
        sendReply(bot, reply, event.getGroupId());
        // 删除成员信息
        shiroGroupMembersService.deleteGroupMemberInfo(groupId, userId);
        return MESSAGE_BLOCK;
    }

    @GroupAdminHandler
    @Order(1)
    public int handleGroupAdmin(Bot bot, GroupAdminNoticeEvent event) {
        Long groupId = event.getGroupId();
        Long userId = event.getUserId();
        Long botId = bot.getSelfId();
        GroupAdminChangeType groupAdminChangeType = GroupAdminChangeType.fromValue(event.getSubType());
        BotReply reply = null;
        if (userId.equals(botId)) {
            switch (groupAdminChangeType) {
                case SET -> {
                    log.info("Bot {} 在群 {} 中被提升为管理员", botId, groupId);
                    reply = botReplyService.getReply(new BotReplyParams(null, BotReplyTemplateType.PROMOTE));
                }
                case UNSET -> {
                    log.info("Bot {} 在群 {} 中被降级为普通成员", botId, groupId);
                    reply = botReplyService.getReply(new BotReplyParams(null, BotReplyTemplateType.DEMOTE));
                }
            }
        }
        sendReply(bot, reply, event.getGroupId());
        shiroGroupMembersService.saveOrUpdateGroupMemberInfo(event);
        return MESSAGE_BLOCK;
    }

    @GroupAddRequestHandler
    @Order(1)
    public int handleGroupAddRequest(Bot bot, GroupAddRequestEvent event) {
        // 处理加群请求的逻辑
        log.info("req = {}", event);
        Long groupId = event.getGroupId();
        QQGroupAutoHandleAddRequestFeatureConfig config = qqGroupService.getOrCreateAutoHandleAddRequestConfig(groupId);
        if (config.getAutoHandleAddRequestEnabled()) {
            GroupAddRequestType groupAddRequestType = GroupAddRequestType.fromValue(event.getSubType());
            bot.setGroupAddRequest(event.getFlag(), groupAddRequestType.getValue(), true, "");
        }
        // 这里可以根据需要自动批准或拒绝加群请求
        return MESSAGE_BLOCK;
    }

    @GroupCardChangeNoticeHandler
    @Order(1)
    public int handleGroupCardChange(GroupCardChangeNoticeEvent event) {
        Long groupId = event.getGroupId();
        Long userId = event.getUserId();
        String newCard = event.getCardNew();
        String oldCard = event.getCardOld();
        log.info("群成员 {} 在群 {} 中修改了群名片: 旧名片='{}', 新名片='{}'", userId, groupId, oldCard, newCard);
        // 这里可以添加其他逻辑，例如更新数据库中的群成员信息等
        shiroGroupMembersService.saveOrUpdateGroupMemberInfo(event);
        return MESSAGE_BLOCK;
    }

    private void sendReply(Bot bot, BotReply reply, Long groupId, Long userId,  boolean at) {
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

    private void sendReply(Bot bot, BotReply reply, Long groupId,Long userId) {
        sendReply(bot, reply, groupId, userId, true);
    }

    private void sendReply(Bot bot, BotReply reply, Long groupId) {
        sendReply(bot, reply, groupId, null, false);
    }

}