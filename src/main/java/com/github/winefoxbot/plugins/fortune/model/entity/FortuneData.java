package com.github.winefoxbot.plugins.fortune.model.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;

import lombok.Builder;
import lombok.Data;

/**
 * @author FlanChan
 * @TableName fortune_data
 */
@TableName(value ="fortune_data")
@Data
@Builder
public class FortuneData implements Serializable {
    @TableId(type = IdType.INPUT)
    private Long userId;
    private Integer starNum;
    private LocalDate fortuneDate;
    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}