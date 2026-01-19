package com.github.winefoxbot.core.aop.handler;

import com.github.winefoxbot.core.context.BotContext;
import com.github.winefoxbot.core.exception.bot.BotException;
import com.github.winefoxbot.core.utils.SendMsgUtil;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.MessageEvent;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

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

    // 复用之前的切入点定义，或者你可以提取到一个公共的 Pointcut类 中
    @Pointcut("(@within(com.mikuac.shiro.annotation.common.Shiro) && @within(org.springframework.stereotype.Component)) && " +
            "(@annotation(com.mikuac.shiro.annotation.AnyMessageHandler) || " +
            "@annotation(com.mikuac.shiro.annotation.GroupMessageHandler) || " +
            "@annotation(com.mikuac.shiro.annotation.PrivateMessageHandler) || " +
            "@annotation(com.mikuac.shiro.annotation.GroupPokeNoticeHandler) || " +
            "@annotation(com.mikuac.shiro.annotation.PrivatePokeNoticeHandler))")
    public void pluginExecutionPointcut() {
    }

    @Around("pluginExecutionPointcut()")
    public Object handlePluginExceptions(ProceedingJoinPoint joinPoint) throws Throwable {
        try {
            // 直接执行业务逻辑
            return joinPoint.proceed();
        } catch (BotException e) {
            // 1. 捕获既然已知的业务异常
            handleException(e.getMessage(), e, joinPoint);
            return null;
        } catch (Exception e) {
            // 2. 捕获未知异常
            handleException("系统内部错误，请联系管理员。", e, joinPoint);
            return null;
        }
    }

    private void handleException(String replyMsg, Throwable e, ProceedingJoinPoint joinPoint) {
        log.error("插件执行异常: [{}] - {}", joinPoint.getSignature().toShortString(), e.getMessage(), e);

        if (BotContext.CURRENT_BOT.isBound() && BotContext.CURRENT_MESSAGE_EVENT.isBound()) {
            Bot bot = BotContext.CURRENT_BOT.get();
            MessageEvent event = BotContext.CURRENT_MESSAGE_EVENT.get();
            // 发送错误提示
            SendMsgUtil.sendMsgByEvent(bot, event, replyMsg, false);
        } else {
            log.warn("捕获到异常，但无法获取 Bot 上下文 (ScopedValue 未绑定)，无法发送错误消息给用户。");
        }
    }
}
