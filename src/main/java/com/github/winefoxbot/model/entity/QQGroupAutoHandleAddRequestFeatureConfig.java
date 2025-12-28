package com.github.winefoxbot.model.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Date;
import lombok.Data;

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