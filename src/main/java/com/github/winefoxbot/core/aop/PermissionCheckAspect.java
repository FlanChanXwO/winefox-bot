package com.github.winefoxbot.core.aop;

import com.github.winefoxbot.core.annotation.PluginFunction;
import com.github.winefoxbot.core.config.app.WineFoxBotProperties;
import com.github.winefoxbot.core.config.app.WineFoxBotRebotProperties;
import com.github.winefoxbot.core.model.entity.ShiroGroupMember;
import com.github.winefoxbot.core.model.enums.GroupMemberRole;
import com.github.winefoxbot.core.model.enums.Permission;
import com.github.winefoxbot.core.service.shiro.ShiroGroupMembersService;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.AnyMessageEvent;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import com.mikuac.shiro.dto.event.message.PrivateMessageEvent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class PermissionCheckAspect {

    private final WineFoxBotProperties wineFoxBotProperties;
    private final ShiroGroupMembersService groupMembersService;

    @Around("@annotation(com.github.winefoxbot.core.annotation.PluginFunction)")
    public Object checkPermission(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        PluginFunction pluginFunction = method.getAnnotation(PluginFunction.class);

        Permission requiredPermission = pluginFunction.permission();
        log.debug("功能 '{}' 需要权限: {}", pluginFunction.name(), requiredPermission);

        if (requiredPermission == Permission.USER) {
            return joinPoint.proceed();
        }

        // 1. 尝试从参数中获取并包装事件信息
        MessageEventWrapper eventWrapper = createEventWrapper(joinPoint);

        if (eventWrapper == null) {
            log.warn("无法进行权限校验：在方法 '{}' 中未找到支持的消息事件参数 (如 GroupMessageEvent 或 PrivateMessageEvent)。", method.getName());
            return null; // 阻止执行，因为无法确定调用者
        }

        // 2. 获取用户权限
        Permission userPermission = getUserPermission(eventWrapper);
        log.debug("用户 {} 在 {} 场景下的权限为: {}", eventWrapper.getUserId(), eventWrapper.getEventType(), userPermission);

        // 3. 比较权限
        if (userPermission.isSufficient(requiredPermission)) {
            return joinPoint.proceed();
        } else {
            // 权限不足，发送回复并阻止
            log.warn("权限不足！用户 {} (权限: {}) 尝试执行功能 '{}' (需要权限: {})",
                    eventWrapper.getUserId(), userPermission, pluginFunction.name(), requiredPermission);
            return null;
        }
    }

    /**
     * 根据事件包装器获取用户权限。
     *
     * @param wrapper 包装后的事件
     * @return 用户的权限
     */
    private Permission getUserPermission(MessageEventWrapper wrapper) {
        WineFoxBotRebotProperties robotProp = wineFoxBotProperties.getRobot();
        List<Long> superUsers = robotProp.getSuperUsers();
        // 任何场景下，超级管理员都拥有最高权限
        if (superUsers != null && superUsers.contains(wrapper.getUserId())) {
            return Permission.SUPERADMIN;
        }

        // 根据事件类型分别处理
        if (wrapper.getEventType() == EventType.GROUP) {
            // 群聊场景：查询数据库获取角色
            ShiroGroupMember member = groupMembersService.lambdaQuery()
                    .eq(wrapper.getReplyTargetId() > 0L, ShiroGroupMember::getGroupId, wrapper.getReplyTargetId()) // getReplyTargetId() 在群聊中是 groupId
                    .eq(ShiroGroupMember::getUserId, wrapper.getUserId())
                    .one();

            GroupMemberRole role = Optional.ofNullable(member)
                    .map(ShiroGroupMember::getRole)
                    .orElse(GroupMemberRole.MEMBER); // 查不到默认为普通成员
            return Permission.fromGroupMemberRole(role);
        } else {
            // 私聊或其他场景：非超级管理员即为普通用户
            return Permission.USER;
        }
    }

    /**
     * 查找方法参数中的消息事件，并将其包装成统一的 MessageEventWrapper。
     *
     * @param joinPoint 切点
     * @return 包装后的事件对象，如果找不到则返回 null
     */
    private MessageEventWrapper createEventWrapper(ProceedingJoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        Bot bot = findArgument(args, Bot.class);
        if (bot == null) return null; // 必须有 Bot 对象

        // 优先查找anyMsg事件
        AnyMessageEvent anyMessageEvent = findArgument(args, AnyMessageEvent.class);
        if (anyMessageEvent != null) {
            Long groupId = anyMessageEvent.getGroupId() != null ? anyMessageEvent.getGroupId() : -1L;
            anyMessageEvent.setGroupId(groupId);
            return new MessageEventWrapper(bot, anyMessageEvent);
        }

        // 然后查找群聊事件
        GroupMessageEvent groupEvent = findArgument(args, GroupMessageEvent.class);
        if (groupEvent != null) {
            return new MessageEventWrapper(bot, groupEvent);
        }

        // 再查找私聊事件
        PrivateMessageEvent privateEvent = findArgument(args, PrivateMessageEvent.class);
        if (privateEvent != null) {
            return new MessageEventWrapper(bot, privateEvent);
        }

        return null; // 未找到任何支持的事件
    }

    private <T> T findArgument(Object[] args, Class<T> type) {
        for (Object arg : args) {
            if (type.isInstance(arg)) {
                return type.cast(arg);
            }
        }
        return null;
    }

    // --- 内部辅助类和枚举 ---

    private enum EventType {GROUP, PRIVATE, UNKNOWN}

    /**
     * 内部类，用于统一不同消息事件的接口。
     */
    @Data
    @AllArgsConstructor
    private static class MessageEventWrapper {
        private final Bot bot;
        private final long userId;
        private final long replyTargetId; // 群聊时是 groupId，私聊时是 userId
        private final EventType eventType;

        public MessageEventWrapper(Bot bot, GroupMessageEvent event) {
            this.bot = bot;
            this.userId = event.getSender().getUserId();
            this.replyTargetId = event.getGroupId();
            this.eventType = EventType.GROUP;
        }

        public MessageEventWrapper(Bot bot, PrivateMessageEvent event) {
            this.bot = bot;
            this.userId = event.getUserId();
            this.replyTargetId = event.getUserId();
            this.eventType = EventType.PRIVATE;
        }

        /**
         * 发送回复消息，自动判断是群聊还是私聊。
         */
        public void reply(String message) {
            if (eventType == EventType.GROUP) {
                bot.sendGroupMsg(replyTargetId, message, false);
            } else if (eventType == EventType.PRIVATE) {
                bot.sendPrivateMsg(replyTargetId, message, false);
            }
        }
    }
}
