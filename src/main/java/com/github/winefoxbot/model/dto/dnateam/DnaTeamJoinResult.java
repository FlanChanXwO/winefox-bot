package com.github.winefoxbot.model.dto.dnateam;

import lombok.Data;

@Data
public class DnaTeamJoinResult {

    private boolean success;
    private String message;
    private int currentCount;
    private boolean full;

    public static DnaTeamJoinResult ok(int count, boolean full) {
        DnaTeamJoinResult r = new DnaTeamJoinResult();
        r.success = true;
        r.currentCount = count;
        r.full = full;
        return r;
    }

    public static DnaTeamJoinResult fail(String msg) {
        DnaTeamJoinResult r = new DnaTeamJoinResult();
        r.success = false;
        r.message = msg;
        return r;
    }
}
