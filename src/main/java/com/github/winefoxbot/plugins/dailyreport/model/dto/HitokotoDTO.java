package com.github.winefoxbot.plugins.dailyreport.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record HitokotoDTO(
        String hitokoto,
        String from
) implements Serializable {}
