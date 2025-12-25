package com.github.winefoxbot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.winefoxbot.model.dto.dnateam.DnaTeamMemberView;
import com.github.winefoxbot.model.entity.DnaTeamMember;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
* @author FlanChan
* @description 针对表【dna_team_member】的数据库操作Mapper
* @createDate 2025-12-24 17:58:50
* @Entity generator.domain.DnaTeamMember
*/
public interface DnaTeamMemberMapper extends BaseMapper<DnaTeamMember> {
    @Select("""
        SELECT *
        FROM dna_team_member
        WHERE team_id = #{teamId}
          AND user_id = #{userId}
        LIMIT 1
    """)
    DnaTeamMember find(long teamId, long userId);

    @Delete("""
        DELETE FROM dna_team_member
        WHERE team_id = #{teamId}
          AND user_id = #{userId}
    """)
    int deleteByTeamAndUser(long teamId, long userId);

    @Delete("""
        DELETE FROM dna_team_member
        WHERE team_id = #{teamId}
    """)
    int deleteByTeam(long teamId);

    @Select("""
        SELECT *
        FROM dna_team_member
        WHERE team_id = #{teamId}
        ORDER BY joined_at ASC
        LIMIT 1
    """)
    DnaTeamMember findEarliestMember(long teamId);

    @Update("""
        UPDATE dna_team_member
        SET role = 1
        WHERE id = #{memberId}
    """)
    int promoteToLeader(long memberId);

    @Select("""
        SELECT m.user_id AS userId,
               m.role AS role,
               m.joined_at AS joinedAt
        FROM dna_team_member m
        WHERE m.team_id = #{teamId}
        ORDER BY m.joined_at ASC
    """)
    List<DnaTeamMemberView> listMembers(long teamId);

    @Delete("""
        DELETE FROM dna_team  
        WHERE group_id = #{groupId}
          AND create_user_id = #{userId}
    """)
    int deleteByGroupAndUser(Long groupId, Long userId);
}




