package com.github.winefoxbot.plugins.setu.model.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.github.winefoxbot.core.model.type.BaseEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SetuApiType implements BaseEnum<String> {

    /**
     * SexNyanRun API
     */
    SEX_NYAN("sexNyanRunApiService", "SexNyanRun"),

    /**
     * Lolicon API V2
     */
    LOLICON("loliconApiService", "LoliconV2");


    @EnumValue
    @JsonValue
    private final String value;

    private final String description;

    @JsonCreator
    public static SetuApiType fromValue(String value) {
        for (SetuApiType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        return LOLICON;
    }
}