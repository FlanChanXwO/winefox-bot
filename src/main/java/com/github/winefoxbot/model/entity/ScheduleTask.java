package com.github.winefoxbot.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.github.winefoxbot.model.enums.ScheduleType;
import com.github.winefoxbot.model.enums.TaskStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 调度任务实体类，对应数据库中的 shiro_schedule_task 表
 * 使用 MyBatis-Plus 注解进行 ORM 映射
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("shiro_schedule_task") // 显式映射到 shiro_schedule_task 表
public class ScheduleTask implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 任务的机器唯一ID，由程序生成（如UUID），用作主键。
     * @TableId 指定主键。
     * value = "task_id" 明确映射到数据库的 task_id 列 (虽然MP默认会做驼峰转下划线，但显式指定更清晰)。
     * type = IdType.ASSIGN_UUID 表明ID由程序在插入前设置。
     */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /**
     *  用于防止重复订阅，例如 "pixiv_rank_push:group:123456"。
     *  -- UNIQUE 约束由数据库保证其唯一性，查询速度极快。
     *  -- 允许为 NULL，以兼容那些没有“订阅”概念的一次性或内部任务。
     */
    private String taskId;

    /**
     * 任务的可读描述，用于在UI上展示。
     * MP会自动将驼峰命名法映射到下划线命名法 (description -> description)，无需额外注解。
     */
    private String description;

    /**
     * 要执行的Spring Bean的名称。
     * (beanName -> bean_name)
     */
    private String beanName;

    /**
     * 要调用的具体方法名。
     * (methodName -> method_name)
     */
    private String methodName;

    /**
     * 调用方法时需要传递的参数，通常是JSON字符串。
     * (taskParams -> task_params)
     */
    private String taskParams;

    /**
     * 任务的调度类型，单次或周期性。
     * (scheduleType -> schedule_type)
     */
    private ScheduleType scheduleType;

    /**
     * Cron表达式，用于定义周期性任务。如果为NULL，则为单次任务。
     * (cronExpression -> cron_expression)
     */
    private String cronExpression;

    /**
     * 任务下一次的计划执行时间，调度器核心依赖字段。
     * (nextExecutionTime -> next_execution_time)
     */
    private LocalDateTime nextExecutionTime;

    /**
     * 任务状态。MyBatis-Plus 默认会将枚举类型按其名称 (name()) 存储为字符串。
     * 这与数据库的 TEXT 类型完美匹配。
     */
    private TaskStatus status;

    /**
     * [可选] 对于固定次数的周期任务，记录总共需要执行的次数。
     * (totalExecutions -> total_executions)
     */
    private Integer totalExecutions;

    /**
     * [可选] 记录已经成功执行了多少次。
     * (executedCount -> executed_count)
     */
    private Integer executedCount;

    /**
     * 任务创建时间。
     * 使用 @TableField 的 fill 属性，在插入时自动填充。
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 任务最后更新时间。
     * 使用 @TableField 的 fill 属性，在插入和更新时自动填充。
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
