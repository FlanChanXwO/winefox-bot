package com.github.winefoxbot.model.dto.dnateam;

import lombok.Data;

@Data
public class DnaTeamKickResult {

    private boolean success;
    private String message;
    private int currentCount;

    public static DnaTeamKickResult ok(int count) {
        DnaTeamKickResult r = new DnaTeamKickResult();
        r.success = true;
        r.currentCount = count;
        return r;
    }

    public static DnaTeamKickResult fail(String msg) {
        DnaTeamKickResult r = new DnaTeamKickResult();
        r.success = false;
        r.message = msg;
        return r;
    }
}
