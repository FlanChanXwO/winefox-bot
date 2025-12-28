package com.github.winefoxbot.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @TableName dna_team
 */
@TableName(value ="dna_team")
@Data
public class DnaTeam implements Serializable {
    private Long id;

    private Integer status;

    private String description;

    private String mode;

    private Integer memberCount;

    private Integer maxMembers;

    private Long groupId;

    private Long createUserId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer version;
    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}