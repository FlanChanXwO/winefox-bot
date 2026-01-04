package com.github.winefoxbot.model.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import com.github.winefoxbot.model.type.BaseEnum; // 引入你的 BaseEnum 接口
import lombok.Getter;

@Getter
public enum PixivRatingLevel implements BaseEnum<Integer> {
    
    ALL_AGES(0, "全年龄"),
    R18(1, "R-18"),
    R18G(2, "R-18G");

    @JsonValue // 让 Jackson 序列化时使用 value 字段
    @EnumValue // 让 MyBatis-Plus 使用 value 字段存储到数据库
    private final Integer value;
    private final String description;

    PixivRatingLevel(Integer value, String description) {
        this.value = value;
        this.description = description;
    }

    /**
     * 提供一个静态方法，方便从API返回的整型值安全地转换为枚举
     * @param apiValue 从 Pixiv API 获取的原始 xRestrict 值
     * @return 对应的 RatingLevel 枚举，如果未知则默认为 ALL_AGES
     */
    public static PixivRatingLevel fromValue(Integer apiValue) {
        for (PixivRatingLevel level : values()) {
            if (level.getValue().equals(apiValue)) {
                return level;
            }
        }
        throw new IllegalArgumentException("Unknown PixivRatingLevel value: " + apiValue);
    }
}
