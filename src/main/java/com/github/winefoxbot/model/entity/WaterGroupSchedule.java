package com.github.winefoxbot.model.entity;


import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalTime;
/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-24-11:53
 */
@Data
@TableName("water_group_schedule") // Maps the entity to the table
public class WaterGroupSchedule {
    @TableId(type = IdType.AUTO) // Maps the primary key with auto-increment
    private Long id;

    private Long groupId;
    private LocalTime time;
}