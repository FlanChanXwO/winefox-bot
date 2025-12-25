package com.github.winefoxbot.model.dto;


import lombok.Data;

import java.time.LocalDate;

@Data
public class WaterGroupMemberStat {
    private Integer id;
    private Long userId;
    private Long groupId;
    private Integer msgCount;
    private String nickname;
    private String avtarUrl;
    private LocalDate date;
}
