package com.github.winefoxbot.core.plugins.event;

import com.github.winefoxbot.core.annotation.plugin.Plugin;
import com.github.winefoxbot.core.model.enums.common.GroupMemberDecreaseType;
import com.github.winefoxbot.core.service.shiro.ShiroGroupMembersService;
import com.github.winefoxbot.core.service.shiro.ShiroGroupRequestsService;
import com.github.winefoxbot.core.service.shiro.ShiroGroupsService;
import com.github.winefoxbot.core.service.shiro.ShiroMessagesService;
import com.mikuac.shiro.annotation.*;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.notice.*;
import com.mikuac.shiro.dto.event.request.GroupAddRequestEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 群事件监听器
 * <p>
 * 这是一个系统级的核心组件，负责维护数据库状态（如成员变动、群组状态、请求记录等）。
 * 它不属于“插件”体系，因此无法被禁用，也不负责发送欢迎消息等交互逻辑。
 * </p>
 *
 * @author FlanChan
 */
@Component
@Plugin(name = "群事件监听器", hidden = true, canDisable = false ,builtIn = true)
@Slf4j
@RequiredArgsConstructor
public class GroupEventListenerPlugin {

    private final ShiroMessagesService shiroMessagesService;
    private final ShiroGroupMembersService shiroGroupMembersService;
    private final ShiroGroupRequestsService shiroGroupRequestsService;
    private final ShiroGroupsService groupsService;

    /**
     * 监听群消息撤回，同步删除数据库中的消息记录
     */
    @GroupMsgDeleteNoticeHandler
    public void handleGroupMessageDelete(GroupMsgDeleteNoticeEvent event) {
        Integer messageId = event.getMessageId();
        shiroMessagesService.removeByMessageId(messageId);
    }

    /**
     * 监听群成员减少，同步删除数据库记录
     */
    @GroupDecreaseHandler
    public void handleGroupDecrease(Bot bot, GroupDecreaseNoticeEvent event) {
        Long groupId = event.getGroupId();
        Long userId = event.getUserId();
        Long operatorId = event.getOperatorId();
        GroupMemberDecreaseType type = GroupMemberDecreaseType.fromValue(event.getSubType());

        // 如果是机器人自己被踢出，删除该群的所有数据
        if (type == GroupMemberDecreaseType.KICK_ME) {
            log.info("Bot {} 被踢出群 {}，操作者: {}", bot.getSelfId(), groupId, operatorId);
            groupsService.deleteGroupInfo(groupId, bot.getSelfId());
        }

        // 无论何种方式离开，都删除成员信息
        shiroGroupMembersService.deleteGroupMemberInfo(groupId, userId);
    }

    /**
     * 监听管理员变动，更新数据库中的角色信息
     */
    @GroupAdminHandler
    public void handleGroupAdmin(Bot bot, GroupAdminNoticeEvent event) {
        // 更新数据库中的群成员信息 (Admin/User)
        shiroGroupMembersService.saveOrUpdateGroupMemberInfo(event);
    }

    /**
     * 监听群名片修改，更新数据库
     */
    @GroupCardChangeNoticeHandler
    public void handleGroupCardChange(GroupCardChangeNoticeEvent event) {
        Long groupId = event.getGroupId();
        Long userId = event.getUserId();
        log.info("群成员 {} 在群 {} 中修改了群名片", userId, groupId);
        shiroGroupMembersService.saveOrUpdateGroupMemberInfo(event);
    }

    /**
     * 监听加群/邀请请求，保存到数据库以供审核
     */
    @GroupAddRequestHandler
    public void handleGroupAddRequest(Bot bot, GroupAddRequestEvent event) {
        log.info("收到群添加请求: {}", event);
        shiroGroupRequestsService.saveGroupAddRequest(event);
    }
}