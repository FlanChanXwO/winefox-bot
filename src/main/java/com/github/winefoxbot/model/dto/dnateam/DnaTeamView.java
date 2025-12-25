package com.github.winefoxbot.model.dto.dnateam;

import lombok.Data;
import java.util.List;

@Data
public class DnaTeamView {

    private long teamId;
    private int memberCount;
    private boolean full;
    private List<DnaTeamMemberView> members;
}
