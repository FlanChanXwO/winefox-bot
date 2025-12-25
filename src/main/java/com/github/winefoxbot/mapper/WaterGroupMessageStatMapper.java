package com.github.winefoxbot.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.winefoxbot.model.entity.WaterGroupMessageStat;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface WaterGroupMessageStatMapper extends BaseMapper<WaterGroupMessageStat> {

    @Update("INSERT INTO water_group_day_msg (user_id, group_id, msg_count) VALUES (#{userId}, #{groupId}, 1) " +
            "ON CONFLICT(user_id, group_id) DO UPDATE SET msg_count = msg_count + 1")
    void upsert(@Param("userId") long userId, @Param("groupId") long groupId);
}
