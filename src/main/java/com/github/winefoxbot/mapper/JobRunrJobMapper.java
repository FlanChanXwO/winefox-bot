package com.github.winefoxbot.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface JobRunrJobMapper {


    /**
     * 根据 recurringJobId 删除所有处于 SCHEDULED 状态的任务。
     *
     * @param recurringJobId 要删除的任务的 recurringJobId
     * @return 被删除的任务数量（受影响的行数）
     */
    @Delete("DELETE FROM jobrunr_jobs WHERE state = 'SCHEDULED' AND CAST(jobasjson AS jsonb)->>'recurringJobId' = #{recurringJobId}")
    int deleteScheduledJobsByRecurringId(@Param("recurringJobId") String recurringJobId);
}
