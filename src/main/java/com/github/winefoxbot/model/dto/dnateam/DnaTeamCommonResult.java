package com.github.winefoxbot.model.dto.dnateam;

import lombok.Data;

@Data
public class DnaTeamCommonResult {

    private boolean success;
    private String message;

    public static DnaTeamCommonResult ok() {
        DnaTeamCommonResult r = new DnaTeamCommonResult();
        r.success = true;
        return r;
    }

    public static DnaTeamCommonResult fail(String msg) {
        DnaTeamCommonResult r = new DnaTeamCommonResult();
        r.success = false;
        r.message = msg;
        return r;
    }
}
