package com.github.winefoxbot.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.winefoxbot.core.model.entity.ShiroMessage;
import com.github.winefoxbot.core.model.entity.ShiroUserMessage;
import com.github.winefoxbot.core.model.enums.MessageType;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
* @author FlanChan
* @description 针对表【shiro_messages】的数据库操作Mapper
* @createDate 2025-12-20 07:46:49
* @Entity generator.domain.ShiroMessages
*/
public interface ShiroMessagesMapper extends BaseMapper<ShiroMessage> {

    /**
     * 查詢詳細的用戶訊息列表（包含暱稱和羣名片）
     *
     * @param sessionId     ID
     * @param messageType  訊息類型
     * @return 包含詳細訊息的列表
     */
    List<ShiroUserMessage> selectUserMessages(@Param("sessionId") Long sessionId , @Param("messageType") MessageType messageType, @Param("limit") int limit);
}




