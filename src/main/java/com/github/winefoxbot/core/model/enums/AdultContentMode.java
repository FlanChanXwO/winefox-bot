package com.github.winefoxbot.core.model.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.github.winefoxbot.core.model.type.BaseEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AdultContentMode implements BaseEnum<String> {
    
    SFW("sfw", "🟢 安全模式 (SFW)"),
    R18("r18", "🔞 仅限成人 (R18)"),
    MIX("mix", "🔀 混合模式 (MIX)");

    @EnumValue
    @JsonValue
    private final String value;
    
    // 增加一个描述字段，用于前端下拉框的 Label 显示
    private final String description;
    @JsonCreator
    public static AdultContentMode fromValue(String value) {
        for (AdultContentMode mode : values()) {
            if (mode.value.equalsIgnoreCase(value)) {
                return mode;
            }
        }
        // 默认返回 SFW，防止配置错误导致崩溃
        return SFW;
    }
}
