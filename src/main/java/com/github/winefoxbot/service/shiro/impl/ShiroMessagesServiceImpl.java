package com.github.winefoxbot.service.shiro.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.winefoxbot.config.WineFoxBotConfig;
import com.github.winefoxbot.model.entity.ShiroMessage;
import com.github.winefoxbot.service.shiro.ShiroMessagesService;
import com.github.winefoxbot.mapper.ShiroMessagesMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
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

    private final WineFoxBotConfig wineFoxBotConfig;

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
    public List<ShiroMessage> findLatestMessagesForContext(Long sessionId, String sessionType, int limit) {
        QueryWrapper<ShiroMessage> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("message_type", sessionType);

        if ("group".equals(sessionType)) {
            queryWrapper.eq("group_id", sessionId);
        } else { // This will now correctly handle the "private" case.
            // 创建一个可以容纳不同类型对象的列表
            List<Long> combinedList = new ArrayList<>();

            // 1. 添加 String 类型的 sessionId
            combinedList.add(sessionId);

            // 2. 添加 List<Long> 中的所有元素
            List<Long> userIds = wineFoxBotConfig.getBot();
            combinedList.addAll(userIds);
            queryWrapper.in("user_id", combinedList);
        }

        queryWrapper.orderByDesc("id").last("LIMIT " + limit);
        return this.list(queryWrapper);
    }
}




