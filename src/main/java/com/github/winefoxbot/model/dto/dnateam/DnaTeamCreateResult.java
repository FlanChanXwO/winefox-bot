package com.github.winefoxbot.model.dto.dnateam;

import lombok.Data;

@Data
public class DnaTeamCreateResult {

    private boolean success;
    private String message;
    private Long teamId;

    public static DnaTeamCreateResult ok(Long teamId) {
        DnaTeamCreateResult r = new DnaTeamCreateResult();
        r.success = true;
        r.teamId = teamId;
        return r;
    }

    public static DnaTeamCreateResult fail(String msg) {
        DnaTeamCreateResult r = new DnaTeamCreateResult();
        r.success = false;
        r.message = msg;
        return r;
    }
}
