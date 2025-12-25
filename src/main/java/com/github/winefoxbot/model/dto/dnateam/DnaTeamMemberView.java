package com.github.winefoxbot.model.dto.dnateam;

import lombok.Data;

@Data
public class DnaTeamMemberView {

    private long userId;
    private String nickname;
    private boolean leader;
}
