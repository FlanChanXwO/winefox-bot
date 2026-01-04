package com.github.winefoxbot.core.service.shiro.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.winefoxbot.core.mapper.ShiroMessagesMapper;
import com.github.winefoxbot.core.model.entity.ShiroMessage;
import com.github.winefoxbot.core.model.entity.ShiroUserMessage;
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

//    private final WineFoxBotConfig wineFoxBotConfig;

    @Override
    public void clearConversation(Long sessionId, String sessionType) {
        QueryWrapper<ShiroMessage> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("message_type", sessionType);
        if ("group".equals(sessionType)) {
            queryWrapper.eq("group_id", sessionId);
        } else {
            queryWrapper.eq("user_id", sessionId);
        }
        this.remove(queryWrapper);
    }

    @Override
        public List<ShiroUserMessage> findLatestMessagesForContext(Long sessionId, String sessionType, int limit) {
        return this.baseMapper.selectUserMessages(sessionId,sessionType,limit);
    }
}




