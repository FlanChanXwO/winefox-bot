package com.github.winefoxbot.service.dnateam;

import com.github.winefoxbot.model.dto.dnateam.*;
import com.github.winefoxbot.model.entity.DnaTeam;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Optional;

/**
* @author FlanChan
* @description 针对表【dna_team】的数据库操作Service
* @createDate 2025-12-24 17:38:47
*/
public interface DnaTeamService extends IService<DnaTeam> {

    DnaTeamCreateResult createTeam(Long groupId, Long userId);

    DnaTeamJoinResult joinTeam(Long groupId, Long userId);

    DnaTeamLeaveResult leaveTeam(Long groupId, Long userId);

    DnaTeamKickResult kickMember(Long groupId, Long operatorId, Long targetUserId);

    DnaTeamCommonResult dismissTeam(Long groupId, Long operatorId);

    /* ===================== 查看队伍 ===================== */
    Optional<DnaTeamView> getTeamView(Long groupId, Long userId);

    /** 查看我所在的队伍 */
    Optional<DnaTeamView> getMyTeam(Long groupId, Long userId);

    /** 查看本群所有队伍 */
    List<DnaTeamView> listGroupTeams(Long groupId);
}
