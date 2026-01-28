package com.github.winefoxbot.plugins.deerpipe.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("deer_user_config")
public class DeerUserConfig {
    @TableId(type = IdType.INPUT)
    private Long userId;
    
    /**
     * 是否允许被别人帮鹿
     */
    @Builder.Default
    private Boolean allowHelp = true;

    /**
     * 上一次使用补鹿功能的日期（用于限制每天一次）
     */
    private LocalDate lastRetroDate;
}
