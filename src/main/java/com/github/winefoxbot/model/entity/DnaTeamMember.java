package com.github.winefoxbot.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.util.Date;
import lombok.Data;

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

    private Date joinedAt;

    private static final long serialVersionUID = 1L;
}