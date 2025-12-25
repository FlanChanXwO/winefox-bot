package com.github.winefoxbot.service.dnateam.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.winefoxbot.mapper.DnaTeamMapper;
import com.github.winefoxbot.mapper.DnaTeamMemberMapper;
import com.github.winefoxbot.model.dto.dnateam.*;
import com.github.winefoxbot.model.entity.DnaTeam;
import com.github.winefoxbot.model.entity.DnaTeamMember;
import com.github.winefoxbot.service.dnateam.DnaTeamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * @author FlanChan
 * @description 针对表【dna_team】的数据库操作Service实现
 * @createDate 2025-12-24 17:38:47
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DnaTeamServiceImpl extends ServiceImpl<DnaTeamMapper, DnaTeam>
        implements DnaTeamService {
    private final DnaTeamMapper teamMapper;
    private final DnaTeamMemberMapper teamMemberMapper;

    @Transactional
    @Override
    public DnaTeamCreateResult createTeam(Long groupId, Long userId) {

        // 防止一个人同时创建多个队伍
        if (findMyActiveTeam(groupId, userId) != null) {
            return DnaTeamCreateResult.fail("你已经在一个队伍中");
        }

        DnaTeam team = new DnaTeam();
        team.setGroupId(groupId);
        team.setStatus(0);
        team.setMemberCount(1);
        team.setMaxMembers(4);
        team.setCreateUserId(userId);
        team.setCreatedAt(LocalDateTime.now());
        team.setUpdatedAt(LocalDateTime.now());
        team.setVersion(0);

        teamMapper.insert(team);

        DnaTeamMember leader = new DnaTeamMember();
        leader.setTeamId(team.getId());
        leader.setUserId(userId);
        leader.setRole(1);
        leader.setJoinedAt(new Date());
        teamMemberMapper.insert(leader);

        return DnaTeamCreateResult.ok(team.getId());
    }

    @Transactional
    public DnaTeamJoinResult joinTeamByUser(Long groupId,
                                            Long userId,
                                            Long targetUserId) {

        if (findMyActiveTeam(groupId, userId) != null) {
            return DnaTeamJoinResult.fail("你已经在一个队伍中");
        }

        DnaTeam targetTeam = teamMapper.selectOne(
                Wrappers.lambdaQuery(DnaTeam.class)
                        .eq(DnaTeam::getGroupId, groupId)
                        .in(DnaTeam::getStatus, 0, 1)
                        .exists(
                                "select 1 from dna_team_member m " +
                                        "where m.team_id = dna_team.id and m.user_id = {0}",
                                targetUserId
                        )
                        .last("limit 1")
        );

        if (targetTeam == null) {
            return DnaTeamJoinResult.fail("对方当前不在任何队伍中");
        }

        return doJoinTeam(targetTeam, userId);
    }

    private DnaTeamJoinResult doJoinTeam(DnaTeam team, Long userId) {

        if (team.getStatus() != 0) {
            return DnaTeamJoinResult.fail("该队伍不可加入");
        }

        if (teamMemberMapper.selectCount(
                Wrappers.lambdaQuery(DnaTeamMember.class)
                        .eq(DnaTeamMember::getTeamId, team.getId())
                        .eq(DnaTeamMember::getUserId, userId)
        ) > 0) {
            return DnaTeamJoinResult.fail("你已经在该队伍中");
        }

        if (team.getMemberCount() >= team.getMaxMembers()) {
            return DnaTeamJoinResult.fail("队伍已满");
        }

        DnaTeamMember member = new DnaTeamMember();
        member.setTeamId(team.getId());
        member.setUserId(userId);
        member.setRole(0);
        member.setJoinedAt(new Date());
        teamMemberMapper.insert(member);

        team.setMemberCount(team.getMemberCount() + 1);
        if (team.getMemberCount().equals(team.getMaxMembers())) {
            team.setStatus(1);
        }
        teamMapper.updateById(team);

        return DnaTeamJoinResult.ok(team.getMemberCount(), team.getStatus() == 1);
    }


    @Transactional
    @Override
    public DnaTeamJoinResult joinTeam(Long groupId, Long userId) {
        DnaTeam team = findActiveTeam(groupId);
        if (team == null) {
            return DnaTeamJoinResult.fail("当前没有可加入的组队");
        }

        if (teamMemberMapper.selectCount(
                Wrappers.lambdaQuery(DnaTeamMember.class)
                        .eq(DnaTeamMember::getTeamId, team.getId())
                        .eq(DnaTeamMember::getUserId, userId)
        ) > 0) {
            return DnaTeamJoinResult.fail("你已经在队伍中");
        }

        if (team.getMemberCount() >= team.getMaxMembers()) {
            return DnaTeamJoinResult.fail("队伍已满");
        }

        // 插入成员
        DnaTeamMember member = new DnaTeamMember();
        member.setTeamId(team.getId());
        member.setUserId(userId);
        member.setRole(0);
        member.setJoinedAt(new Date());
        teamMemberMapper.insert(member);

        team.setMemberCount(team.getMemberCount() + 1);
        if (team.getMemberCount().equals(team.getMaxMembers())) {
            team.setStatus(1);
        }
        teamMapper.updateById(team);

        return DnaTeamJoinResult.ok(team.getMemberCount(), team.getStatus() == 1);
    }

    @Transactional
    @Override
    public DnaTeamLeaveResult leaveTeam(Long groupId, Long userId) {

        DnaTeam team = teamMapper.findActiveTeamByGroupAndUser(groupId, userId);
        if (team == null) {
            return DnaTeamLeaveResult.fail("当前没有组队");
        }

        DnaTeamMember self =
                teamMemberMapper.find(team.getId(), userId);
        if (self == null) {
            return DnaTeamLeaveResult.fail("你不在队伍中");
        }

        if (team.getMemberCount() == 1) {
            teamMemberMapper.deleteByGroupAndUser(groupId,userId);
            return DnaTeamLeaveResult.ok(0);
        }
        teamMapper.decreaseMemberCount(team.getId());

        // 队长离开，转让队长
        if (self.getRole() == 1) {
            DnaTeamMember next =
                    teamMemberMapper.findEarliestMember(team.getId());
            if (next != null) {
                teamMemberMapper.promoteToLeader(next.getId());
            }
        }

        return DnaTeamLeaveResult.ok(team.getMemberCount() - 1);
    }

    /* ===================== 队长踢人 ===================== */

    @Transactional
    @Override
    public DnaTeamKickResult kickMember(Long groupId,
                                        Long operatorId,
                                        Long targetUserId) {

        DnaTeam team =
                teamMapper.findActiveTeamByGroupAndUser(groupId, operatorId);
        if (team == null) {
            return DnaTeamKickResult.fail("当前没有进行中的组队");
        }

        DnaTeamMember operator =
                teamMemberMapper.find(team.getId(), operatorId);
        if (operator == null || operator.getRole() != 1) {
            return DnaTeamKickResult.fail("只有队长可以踢人");
        }

        DnaTeamMember target =
                teamMemberMapper.find(team.getId(), targetUserId);
        if (target == null) {
            return DnaTeamKickResult.fail("该成员不在队伍中");
        }
        if (target.getRole() == 1) {
            return DnaTeamKickResult.fail("不能踢出队长");
        }

        teamMemberMapper.deleteByTeamAndUser(team.getId(), targetUserId);
        teamMapper.decreaseMemberCount(team.getId());

        return DnaTeamKickResult.ok(team.getMemberCount() - 1);
    }

    /* ===================== 解散队伍 ===================== */

    @Transactional
    @Override
    public DnaTeamCommonResult dismissTeam(Long groupId, Long userId) {

        DnaTeam team =
                teamMapper.findActiveTeamByGroupAndUser(groupId, userId);
        if (team == null) {
            return DnaTeamCommonResult.fail("当前没有组队");
        }

        DnaTeamMember leader =
                teamMemberMapper.find(team.getId(), userId);
        if (leader == null || leader.getRole() != 1) {
            return DnaTeamCommonResult.fail("只有队长可以解散队伍");
        }

        teamMapper.deleteById(team.getId());
        teamMemberMapper.deleteByTeam(team.getId());

        return DnaTeamCommonResult.ok();
    }

    /* ===================== 查看队伍 ===================== */
    @Override
    public Optional<DnaTeamView> getTeamView(Long groupId, Long userId) {

        DnaTeam team =
                teamMapper.findActiveTeamByGroupAndUser(groupId, userId);
        if (team == null) {
            return Optional.empty();
        }

        List<DnaTeamMemberView> members =
                teamMemberMapper.listMembers(team.getId());

        DnaTeamView view = new DnaTeamView();
        view.setTeamId(team.getId());
        view.setMemberCount(team.getMemberCount());
        view.setFull(team.getMemberCount() >= team.getMaxMembers());
        view.setMembers(members);

        return Optional.of(view);
    }

    @Override
    public Optional<DnaTeamView> getMyTeam(Long groupId, Long userId) {

        DnaTeam team = findMyActiveTeam(groupId, userId);
        if (team == null) {
            return Optional.empty();
        }

        return Optional.of(buildTeamView(team));
    }

    @Override
    public List<DnaTeamView> listGroupTeams(Long groupId) {

        List<DnaTeam> teams = findGroupActiveTeams(groupId);

        return teams.stream()
                .map(this::buildTeamView)
                .toList();
    }

    private DnaTeamView buildTeamView(DnaTeam team) {

        List<DnaTeamMemberView> members =
                teamMemberMapper.selectList(
                        Wrappers.lambdaQuery(DnaTeamMember.class)
                                .eq(DnaTeamMember::getTeamId, team.getId())
                                .orderByAsc(DnaTeamMember::getJoinedAt)
                ).stream().map(m -> {
                    DnaTeamMemberView v = new DnaTeamMemberView();
                    v.setUserId(m.getUserId());
                    v.setLeader(m.getRole() == 1);
                    v.setNickname(String.valueOf(m.getUserId())); // 插件层替换昵称
                    return v;
                }).toList();

        DnaTeamView view = new DnaTeamView();
        view.setTeamId(team.getId());
        view.setMemberCount(team.getMemberCount());
        view.setFull(team.getMemberCount() >= team.getMaxMembers());
        view.setMembers(members);

        return view;
    }


    private List<DnaTeam> findGroupActiveTeams(Long groupId) {
        return teamMapper.selectList(
                Wrappers.lambdaQuery(DnaTeam.class)
                        .eq(DnaTeam::getGroupId, groupId)
                        .in(DnaTeam::getStatus, 0, 1)
                        .orderByAsc(DnaTeam::getCreatedAt)
        );
    }


    private DnaTeam findMyActiveTeam(Long groupId, Long userId) {
        return teamMapper.selectOne(
                Wrappers.lambdaQuery(DnaTeam.class)
                        .eq(DnaTeam::getGroupId, groupId)
                        .in(DnaTeam::getStatus, 0, 1)
                        .exists(
                                "select 1 from dna_team_member m " +
                                        "where m.team_id = dna_team.id and m.user_id = {0}", userId
                        )
                        .last("limit 1")
        );
    }
    private DnaTeam findActiveTeam(Long groupId) {
        return teamMapper.selectOne(
                Wrappers.lambdaQuery(DnaTeam.class)
                        .eq(DnaTeam::getGroupId, groupId)
                        .in(DnaTeam::getStatus, 0, 1)
                        .last("limit 1")
        );
    }

}




