package com.github.winefoxbot.plugins;

import com.github.winefoxbot.model.dto.GroupEventMessage;
import com.github.winefoxbot.service.bot.QQGroupService;
import com.github.winefoxbot.utils.BotUtils;
import com.mikuac.shiro.annotation.GroupDecreaseHandler;
import com.mikuac.shiro.annotation.GroupIncreaseHandler;
import com.mikuac.shiro.annotation.common.Shiro;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.notice.GroupDecreaseNoticeEvent;
import com.mikuac.shiro.dto.event.notice.GroupIncreaseNoticeEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-04-11:57
 */
@Shiro
@Component
@Slf4j
@RequiredArgsConstructor
public class QQGroupPlugin{

    private final QQGroupService qqGroupService;

    /**
     * 群成员增加事件处理器
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
        String username = BotUtils.getGroupMemberNickname(bot,groupId,userId,false);
        qqGroupService.handleWelcomeMessage(bot, new GroupEventMessage(userId, username,null, event, groupId));
    }

    /**
     * 群成员减少事件处理器
     * @param bot
     * @param event
     */
    @GroupDecreaseHandler
    public void handleGroupDecrease(Bot bot, GroupDecreaseNoticeEvent event){
        Long groupId = event.getGroupId();
        Long userId = event.getUserId();
        Long botId = bot.getSelfId();
        if (userId.equals(botId)) {
            log.info("Bot {} 被移出群 {}", botId, groupId);
            return;
        }
        String username = BotUtils.getGroupMemberNickname(bot,groupId,userId,true);
        qqGroupService.handleFarewellMessage(bot, new GroupEventMessage(userId, username,null, event, groupId));
    }


}