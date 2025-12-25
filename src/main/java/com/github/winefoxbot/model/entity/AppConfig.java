package com.github.winefoxbot.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;


/**
 * 应用配置实体类
 */
@Data
@TableName("app_config")
@Accessors(chain = true) // 开启链式setter
public class AppConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("scope")
    private String scope;
    @TableField("scope_id")
    private String scopeId;
    @TableField("config_group")
    private String configGroup;
    @TableField("config_key")
    private String configKey;
    @TableField("config_value")
    private String configValue;
    @TableField("description")
    private String description;
}
