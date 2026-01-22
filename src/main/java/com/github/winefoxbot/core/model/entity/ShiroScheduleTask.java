package com.github.winefoxbot.core.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.github.winefoxbot.core.model.enums.common.PushTargetType;
import com.github.winefoxbot.core.model.type.GenericEnumTypeHandler;
import com.github.winefoxbot.core.model.type.PGJsonbTypeHandler;
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
@TableName(value = "shiro_schedule_task",autoResultMap = true) // 显式映射到 shiro_schedule_task 表
public class ShiroScheduleTask implements Serializable {
    @TableId(type = IdType.AUTO)
    private Integer id;

    private Long botId;
    @TableField(typeHandler = GenericEnumTypeHandler.class)
    private PushTargetType targetType;

    private Long targetId;

    private String taskType;

    @TableField(typeHandler = PGJsonbTypeHandler.class)
    private Object taskParam;

    private String cronExpression;

    private Boolean isEnabled;

    private String description;

    private LocalDateTime lastRunAt;

    @TableField(fill = FieldFill.INSERT, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE, updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

}
