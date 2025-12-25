package com.github.winefoxbot.model.entity;


import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;

@Data
@TableName("water_group_msg_stat")
public class WaterGroupMessageStat {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private Long userId;
    private Long groupId;
    private Integer msgCount;
    private LocalDate date;
}
