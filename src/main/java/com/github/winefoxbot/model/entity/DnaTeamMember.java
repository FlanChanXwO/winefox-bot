package com.github.winefoxbot.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Date;

/**
 * @TableName dna_team_member
 */
@TableName(value ="dna_team_member")
@Data
public class DnaTeamMember implements Serializable {
    private Long id;

    private Long teamId;

    private Long userId;

    private Integer role;

    private LocalDateTime joinedAt;
    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}