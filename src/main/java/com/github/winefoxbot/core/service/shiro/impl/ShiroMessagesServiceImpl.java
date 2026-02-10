package com.github.winefoxbot.core.service.shiro.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.winefoxbot.core.mapper.ShiroMessagesMapper;
import com.github.winefoxbot.core.model.entity.ShiroMessage;
import com.github.winefoxbot.core.model.entity.ShiroUserMessage;
import com.github.winefoxbot.core.model.enums.common.MessageType;
import com.github.winefoxbot.core.service.shiro.ShiroMessagesService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author FlanChan
 * @description 针对表【shiro_messages】的数据库操作Service实现
 * @createDate 2025-12-20 07:46:49
 */
@Service
@RequiredArgsConstructor
public class ShiroMessagesServiceImpl extends ServiceImpl<ShiroMessagesMapper, ShiroMessage>
        implements ShiroMessagesService {


    @Override
    public void clearConversation(Long sessionId, MessageType messageType) {
        LambdaQueryWrapper<ShiroMessage> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ShiroMessage::getMessageType, messageType);
        queryWrapper.eq(ShiroMessage::getSessionId, sessionId);
        this.remove(queryWrapper);
    }

    @Override
        public List<ShiroUserMessage> findLatestMessagesForContext(Long sessionId, MessageType messageType, int limit) {
        return this.baseMapper.selectUserMessages(sessionId,messageType,limit);
    }

    @Override
    public boolean removeByMessageId(Integer messageId) {
        LambdaQueryWrapper<ShiroMessage> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ShiroMessage::getMessageId,messageId);
        return this.remove(queryWrapper);
    }

    @Override
    public ShiroUserMessage findByMessageId(Long messageId) {
        // 此处调用了 baseMapper 中一个新增的 selectUserMessageByMessageId 方法。
        // 你需要在 ShiroMessagesMapper.xml 中添加对应的 <select> 语句来实现它，其功能类似于 selectUserMessages，但通过消息ID查询单个结果。
        return this.baseMapper.selectUserMessageByMessageId(messageId);
    }
}
