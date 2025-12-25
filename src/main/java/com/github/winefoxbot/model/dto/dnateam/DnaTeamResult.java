package com.github.winefoxbot.model.dto.dnateam;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DnaTeamResult {

    private boolean success;
    private String message;

    public static DnaTeamResult ok() {
        return new DnaTeamResult(true, null);
    }

    public static DnaTeamResult fail(String msg) {
        return new DnaTeamResult(false, msg);
    }
}
