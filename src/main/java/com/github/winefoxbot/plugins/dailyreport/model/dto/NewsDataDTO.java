package com.github.winefoxbot.plugins.dailyreport.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NewsDataDTO(Data data) implements Serializable {

    public record Data(
            Date date,
            List<String> news,
            String tip,
            String image) {}
}

