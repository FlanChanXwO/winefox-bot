package com.github.winefoxbot.plugins.qqgroup.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @TableName qq_group_auto_handle_add_request_feature_config
 */
@TableName(value ="qq_group_auto_handle_add_request_feature_config")
@Data
public class QQGroupAutoHandleAddRequestFeatureConfig implements Serializable {
    @TableId(type = IdType.AUTO)
    private Integer id;

    private Long groupId;

    private Boolean autoHandleAddRequestEnabled;

    private Boolean blockFeatureEnabled;


    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}