package com.github.winefoxbot.core.service.shiro.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.winefoxbot.core.mapper.ShiroMessagesMapper;
import com.github.winefoxbot.core.model.entity.ShiroMessage;
import com.github.winefoxbot.core.model.entity.ShiroUserMessage;
import com.github.winefoxbot.core.model.enums.MessageType;
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
}




