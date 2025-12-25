package com.github.winefoxbot.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.util.Date;
import lombok.Data;

/**
 * @TableName pixiv_rank_push_schedule
 */
@TableName(value ="pixiv_rank_push_schedule")
@Data
public class PixivRankPushSchedule implements Serializable {
    private Integer id;

    private Long groupId;

    private Date time;

    private static final long serialVersionUID = 1L;
}