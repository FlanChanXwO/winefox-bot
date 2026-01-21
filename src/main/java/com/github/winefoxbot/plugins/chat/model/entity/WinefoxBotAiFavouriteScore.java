package com.github.winefoxbot.plugins.chat.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import lombok.Data;

/**
 * @TableName winefox_bot_ai_favourite_score
 */
@TableName(value ="winefox_bot_ai_favourite_score")
@Data
public class WinefoxBotAiFavouriteScore implements Serializable {
    @TableId(type = IdType.INPUT)
    private Long userId;

    private Integer score;
    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}