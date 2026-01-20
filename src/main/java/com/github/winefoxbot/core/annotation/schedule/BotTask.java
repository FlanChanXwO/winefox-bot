package com.github.winefoxbot.core.annotation.schedule;

import com.github.winefoxbot.core.model.enums.PushTargetType;
import org.springframework.stereotype.Component;

import java.lang.annotation.*;

/**
 * 标记一个类为 Bot 的定时任务处理器
 * 用于生成前端 WebUI 的可读名称和绑定唯一 Key
 * @author FlanChan
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface BotTask {
    /**
     * 任务唯一标识 (存入数据库 task_type 字段)
     * 必须全局唯一，例如: pixiv_rank_push
     */
    String key();

    /**
     * 前端显示的友好名称
     * 例如: P站每日排行榜
     */
    String name();

    /**
     * 任务描述/帮助信息
     */
    String description() default "";

    /**
     * 推送目标类型（决定了任务配置选择）
     */
    PushTargetType targetType() default PushTargetType.GLOBAL;

    /**
     * 参数示例 JSON (方便前端回填)
     */
    String paramExample() default "{}";
}
