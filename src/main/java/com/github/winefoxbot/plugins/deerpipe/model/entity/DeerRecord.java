package com.github.winefoxbot.plugins.deerpipe.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @TableName deer_record
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value ="deer_record")
public class DeerRecord implements Serializable {
    @TableId(type = IdType.ASSIGN_UUID)
    private String uuid;

    private Long userId;

    private Integer year;

    private Integer month;

    private Integer day;
    /**
     * 签到次数，默认为 1
     */
    @Builder.Default
    private Integer count = 1;
    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}