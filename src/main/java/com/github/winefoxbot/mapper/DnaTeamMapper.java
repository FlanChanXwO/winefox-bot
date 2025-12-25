package com.github.winefoxbot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.winefoxbot.model.entity.DnaTeam;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
* @author FlanChan
* @description 针对表【dna_team】的数据库操作Mapper
* @createDate 2025-12-24 17:58:50
* @Entity generator.domain.DnaTeam
*/
public interface DnaTeamMapper extends BaseMapper<DnaTeam> {
    @Select("""
        SELECT t.*
        FROM dna_team t
        JOIN dna_team_member m ON m.team_id = t.id
        WHERE t.group_id = #{groupId}
          AND t.status = 0
        LIMIT 1
    """)
    DnaTeam findActiveTeamByGroupId(long groupId);

    @Update("""
        UPDATE dna_team
        SET member_count = member_count - 1,
            updated_at = now(),
            version = version + 1
        WHERE id = #{teamId}
          AND member_count > 0
    """)
    int decreaseMemberCount(long teamId);

    @Update("""
        UPDATE dna_team
        SET status = 2,
            updated_at = now(),
            version = version + 1
        WHERE id = #{teamId}
    """)
    int dismiss(long teamId);

    @Select("""
        SELECT t.*
        FROM dna_team t
        JOIN dna_team_member m ON m.team_id = t.id
        WHERE t.group_id = #{groupId}
          AND t.status = 0
          AND m.user_id = #{userId}
    """)
    DnaTeam findActiveTeamByGroupAndUser(Long groupId, Long userId);
}




