package com.github.winefoxbot.model.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonValue;
import com.github.winefoxbot.model.type.BaseEnum;
import com.github.winefoxbot.model.type.GenericEnumTypeHandler;
import com.github.winefoxbot.model.type.StringListTypeHandler;
import lombok.Data;
import lombok.Getter;

import java.util.List;

/**
 * Pixiv 推荐推送订阅信息
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-12-20:50
 */
@Data
@TableName("pixiv_recommend_push_subscription")
public class PixivRankPushSubscription {
    @TableId
    private Integer id;

    /**
     * 订阅类型
     */
    @TableField(value = "type",typeHandler = GenericEnumTypeHandler.class)
    private Type type;
    /**
     * 群号
     */
    @TableField("group_id")
    private Long groupId;
    /**
     * UID
     */
    @TableField("user_id")
    private Long userId;
    /**
     * 是否订阅R18
     */
    @TableField("enabled_r18")
    private Boolean enabledR18;
    /**
     * 订阅范围
     */
    @TableField(value = "subscription_ranges",typeHandler = StringListTypeHandler.class)
    private List<String> subscriptionRanges;

    @Getter
    public enum Type implements BaseEnum<String> {
        GROUP("GROUP"), USER("USER");
        @JsonValue
        @EnumValue
        private final String value;
        Type(String value) {
            this.value = value;
        }
    }
}