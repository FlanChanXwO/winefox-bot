package com.github.winefoxbot.plugins.dailyreport.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BiliHotwordDTO(List<HotwordItem> list) implements Serializable {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HotwordItem(
            String keyword,
            @JsonProperty("show_name")
            String showName) {
    }
}

