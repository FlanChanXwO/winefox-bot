package com.github.winefoxbot.core.aop.handler;

import cn.hutool.core.util.RandomUtil;
import com.github.winefoxbot.core.context.BotContext;
import com.github.winefoxbot.core.exception.bot.BotException;
import com.github.winefoxbot.core.exception.common.BusinessException;
import com.github.winefoxbot.core.model.enums.common.MessageType;
import com.github.winefoxbot.core.utils.SendMsgUtil;
import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.action.common.ActionData;
import com.mikuac.shiro.dto.action.response.MsgResp;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import com.mikuac.shiro.dto.event.message.MessageEvent;
import com.mikuac.shiro.dto.event.message.PrivateMessageEvent;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 机器人命令异常处理器 (简化版).
 * <p>
 * 依赖 BotContextAspect 提供的上下文，不需要手动解析参数。
 *
 * @author FlanChan (badapple495@outlook.com)
 * @since 2026-01-19
 */
@Aspect
@Component
@Order(1)
@Slf4j
public class GlobalBotExceptionHandler {

    @Pointcut("(" +
            // 场景A: 使用了 @Plugin 自定义注解 (内部含有 @Shiro 和 @Component)
            "@within(com.github.winefoxbot.core.annotation.plugin.Plugin) || " +
            // 场景B: 原生 Shiro 写法 (直接标记 @Shiro 和 @Component)
            "(@within(com.mikuac.shiro.annotation.common.Shiro) && @within(org.springframework.stereotype.Component))" + ") && " +
            "(@annotation(com.mikuac.shiro.annotation.AnyMessageHandler) || " +
            "@annotation(com.mikuac.shiro.annotation.GroupMessageHandler) || " +
            "@annotation(com.mikuac.shiro.annotation.PrivateMessageHandler) || " +
            "@annotation(com.mikuac.shiro.annotation.GroupPokeNoticeHandler) || " +
            "@annotation(com.mikuac.shiro.annotation.PrivatePokeNoticeHandler))")
    public void pluginExecutionPointcut() {}

    private final static List<String> ERROR_MSGS = List.of(
            "坏掉了呢，请稍后再试。",
            "系统出错了，需要主人的支持...",
            "哎呀，出错了！请稍后再试。",
            "系统遇到问题了，请稍后再试。",
            "发生错误了，请稍后再试试吧。"
    );

    @Around("pluginExecutionPointcut()")
    public Object handlePluginExceptions(ProceedingJoinPoint joinPoint) throws Throwable {
        try {
            // 直接执行业务逻辑
            return joinPoint.proceed();
        } catch (BotException | BusinessException e) {
            // 1. 捕获既然已知的业务异常
            handleException(e.getMessage(), e, joinPoint);
            return null;
        } catch (Exception e) {
            // 2. 捕获未知异常
            handleException(RandomUtil.randomEle(ERROR_MSGS), e, joinPoint);
            return null;
        }
    }

    private void handleException(String replyMsg, Throwable e, ProceedingJoinPoint joinPoint) {
        log.error("插件执行异常: [{}] - {}", joinPoint.getSignature().toShortString(), e.getMessage(), e);
        if (BotContext.CURRENT_BOT.isBound() && BotContext.CURRENT_MESSAGE_EVENT.isBound()) {
            Bot bot = BotContext.CURRENT_BOT.get();
            MessageEvent event = BotContext.CURRENT_MESSAGE_EVENT.get();
            // 发送错误提示
            MsgUtils msgbuilder = MsgUtils.builder();
            Integer messageId = null;
            if (event instanceof GroupMessageEvent ev && MessageType.fromValue(ev.getMessageType()).equals(MessageType.GROUP)) {
                msgbuilder.at(ev.getUserId());
                messageId = ev.getMessageId();
            } else if (event instanceof PrivateMessageEvent ev) {
                messageId = ev.getMessageId();
            }
            if (messageId != null) {
                ActionData<MsgResp> msgResp = bot.getMsg(messageId);
                if (msgResp.getRetCode() == 0) {
                    msgbuilder.reply(messageId);
                }
            }
            SendMsgUtil.sendMsgByEvent(bot, event, msgbuilder.text(replyMsg).build(), false);
        } else {
            log.warn("捕获到异常，但无法获取 Bot 上下文 (ScopedValue 未绑定)，无法发送错误消息给用户。");
        }
    }
}
