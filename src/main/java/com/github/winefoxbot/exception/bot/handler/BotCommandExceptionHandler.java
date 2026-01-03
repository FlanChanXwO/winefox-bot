package com.github.winefoxbot.exception.bot.handler;

import cn.hutool.core.lang.Pair;
import com.github.winefoxbot.exception.bot.*;
import com.github.winefoxbot.exception.common.BusinessException;
import com.github.winefoxbot.utils.BotUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.Event;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 机器人命令执行的全局异常处理器.
 * 使用AOP环绕通知来捕获所有插件方法中抛出的异常.
 *
 * @author FlanChan (badapple495@outlook.com)
 * @since 2026-01-04-0:17
 */
@Aspect     // 1. 声明这是一个切面类
@Component  // 2. 将其注册为Spring Bean
@Slf4j
public class BotCommandExceptionHandler {
    /**
     * 定义切入点 (Pointcut)
     * <p>
     * 1. 类级别约束 (within):
     * - @within(com.mikuac.shiro.annotation.common.Shiro): 匹配所在类有 @Shiro 注解的方法。
     * - @within(org.springframework.stereotype.Component): 匹配所在类有 @Component 注解的方法。
     * - 两者用 '&&' 连接，表示必须同时满足。
     * <p>
     * 2. 方法级别约束 (@annotation):
     * - 匹配带有以下任一注解的方法:
     * - @AnyMessageHandler
     * - @GroupMessageHandler
     * - @PrivateMessageHandler
     * - @GroupPokeNoticeHandler
     * - @PrivatePokeNoticeHandler
     * - 多个 @annotation 条件用 '||' (OR) 连接。
     * <p>
     * 最终表达式将类级别和方法级别的约束用 '&&' 结合起来。
     */
    @Pointcut("(@within(com.mikuac.shiro.annotation.common.Shiro) && @within(org.springframework.stereotype.Component)) && " +
            "(@annotation(com.mikuac.shiro.annotation.AnyMessageHandler) || " +
            "@annotation(com.mikuac.shiro.annotation.GroupMessageHandler) || " +
            "@annotation(com.mikuac.shiro.annotation.PrivateMessageHandler) || " +
            "@annotation(com.mikuac.shiro.annotation.GroupPokeNoticeHandler) || " +
            "@annotation(com.mikuac.shiro.annotation.PrivatePokeNoticeHandler))")
    public void pluginExecutionPointcut() {}

    @Around("pluginExecutionPointcut()")
    public Object handlePluginExceptions(ProceedingJoinPoint joinPoint) throws Throwable {
        try {
            return joinPoint.proceed();
        } catch (BusinessException e) {
            log.error("插件执行失败: [{}], 业务异常信息: {}", joinPoint.getSignature().toShortString(), e.getMessage());
            if (e.getSource() != null) {
                e.getSource().printStackTrace();
            } else {
                e.printStackTrace();
            }
            return null;
        } catch (BaseException e) {
            log.error("插件执行失败: [{}], 异常信息: {}", joinPoint.getSignature().toShortString(), e.getMessage());
            Bot bot = e.getBot();
            Event event = e.getEvent();
            if (bot != null && event != null) {
                handleBotCommandException(e, bot, event);
            }
            return null;
        } catch (Exception e) {
            log.error("插件执行时发生未知异常: [{}],", joinPoint.getSignature().toShortString(), e);
            log.error(e.getMessage());
            // 尝试从方法参数中解析 Bot 和 Event
            findBotAndEventFromArgs(joinPoint.getArgs()).ifPresent(pair -> {
                Bot bot = pair.getKey();
                Event event = pair.getValue();
                BotUtils.sendMsgByEvent(bot, event, "发生了一个未知的内部错误...", false);
            });
            return null;
        }
    }

    private void handleBotCommandException(BaseException e, Bot bot, Event event) {
        switch (e) {
            case CommandParseException cpe -> {
                BotUtils.sendMsgByEvent(bot, event, "命令解析错误: " + cpe.getMessage(), false);
            }
            case ExternalServiceException ese -> {
                BotUtils.sendMsgByEvent(bot, event, "外部服务错误: " + ese.getMessage(), false);
            }
            case FeatureNotEnabledException fnee -> {
                BotUtils.sendMsgByEvent(bot, event, "功能未启用: " + fnee.getMessage(), false);
            }
            case InvalidCommandParamsException icpe -> {
                BotUtils.sendMsgByEvent(bot, event, "无效的命令参数: " + icpe.getMessage(), false);
            }
            case PermissionDeniedException pde -> {
                BotUtils.sendMsgByEvent(bot, event, "权限不足: " + pde.getMessage(), false);
            }
            case RateLimitException rle -> {
                BotUtils.sendMsgByEvent(bot, event, "操作过于频繁: " + rle.getMessage(), false);
            }
            case NetworkException ne -> {
                BotUtils.sendMsgByEvent(bot, event, "网络错误: " + ne.getMessage(), false);
            }
            case ReceiveMessageException rme -> {
                BotUtils.sendMsgByEvent(bot, event, "消息接收错误: " + rme.getMessage(), false);
            }
            case ResourceNotFoundException rnfe -> {
                BotUtils.sendMsgByEvent(bot, event, "资源未找到: " + rnfe.getMessage(), false);
            }
            case SendMessageException sme -> {
                BotUtils.sendMsgByEvent(bot, event, "消息发送错误: " + sme.getMessage(), false);
            }
            case TimeoutException te -> {
                BotUtils.sendMsgByEvent(bot, event, "操作超时: " + te.getMessage(), false);
            }
            case PluginExecutionException pee -> {
                BotUtils.sendMsgByEvent(bot, event, "插件执行错误: " + pee.getMessage(), false);
            }
            case AiServiceInvokeException asie -> {
                BotUtils.sendMsgByEvent(bot, event, "AI服务调用错误: " + asie.getMessage(), false);
            }
            default -> log.error("未处理的 BaseException 类型: {}", e.getClass().getName());
        }
    }

    /**
     * 辅助方法：从被拦截方法的参数列表中查找 Bot 和 Event 实例。
     * 用于在捕获到非 BaseException 时，也能尝试发送错误通知。
     *
     * @param args 方法参数数组
     * @return 包含 Bot 和 Event 的 Optional<Pair>
     */
    private Optional<Pair<Bot, Event>> findBotAndEventFromArgs(Object[] args) {
        Bot bot = null;
        Event event = null;
        for (Object arg : args) {
            if (arg instanceof Bot) {
                bot = (Bot) arg;
            } else if (arg instanceof Event) {
                event = (Event) arg;
            }
        }
        if (bot != null && event != null) {
            // org.apache.commons.lang3.tuple.Pair 是一个不错的选择
            // 如果没有引入，可以自己写一个简单的Pair类，或者直接返回一个Map/List
            return Optional.of(Pair.of(bot, event));
        }
        return Optional.empty();
    }

}
