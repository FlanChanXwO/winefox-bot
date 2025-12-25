package com.github.winefoxbot.model.dto.dnateam;

import lombok.Data;

@Data
public class DnaTeamLeaveResult {

    private boolean success;
    private String message;
    private int currentCount;

    public static DnaTeamLeaveResult ok(int count) {
        DnaTeamLeaveResult r = new DnaTeamLeaveResult();
        r.success = true;
        r.currentCount = count;
        return r;
    }

    public static DnaTeamLeaveResult fail(String msg) {
        DnaTeamLeaveResult r = new DnaTeamLeaveResult();
        r.success = false;
        r.message = msg;
        return r;
    }
}
