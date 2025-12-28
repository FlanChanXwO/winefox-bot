package com.github.winefoxbot.service.shiro;

import com.baomidou.mybatisplus.extension.service.IService;
import com.github.winefoxbot.model.entity.ShiroMessage;
import com.github.winefoxbot.model.entity.ShiroUserMessage;

import java.util.List;

/**
* @author FlanChan
* @description 针对表【shiro_messages】的数据库操作Service
* @createDate 2025-12-20 07:46:49
*/
public interface ShiroMessagesService extends IService<ShiroMessage> {

    /**
     * 清空指定会话的消息记录
     *
     * @param sessionId   会话ID
     * @param sessionType 会话类型
     */
    void clearConversation(Long sessionId, String sessionType);

    /**
     * 为AI上下文获取最新的消息列表
     *
     * @param sessionId 会话ID
     * @param sessionType 会话类型
     * @param limit      消息数量限制
     * @return 消息列表
     */
    List<ShiroUserMessage> findLatestMessagesForContext(Long sessionId, String sessionType, int limit);
}
