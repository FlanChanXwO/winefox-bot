package com.github.winefoxbot.core.service.shiro.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.winefoxbot.core.model.entity.ShiroFriends;
import com.github.winefoxbot.core.service.shiro.ShiroFriendsService;
import com.github.winefoxbot.core.mapper.ShiroFriendsMapper;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.core.BotContainer;
import com.mikuac.shiro.dto.action.common.ActionData;
import com.mikuac.shiro.dto.action.response.StrangerInfoResp;
import com.mikuac.shiro.dto.event.notice.FriendAddNoticeEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
* @author FlanChan
* @description 针对表【shiro_friends(存储每个Bot的好友关系)】的数据库操作Service实现
* @createDate 2026-01-07 18:23:26
*/
@Service
@RequiredArgsConstructor
public class ShiroFriendsServiceImpl extends ServiceImpl<ShiroFriendsMapper, ShiroFriends>
    implements ShiroFriendsService{

    private final BotContainer botContainer;

    @Override
    public boolean saveOrUpdateFriend(FriendAddNoticeEvent event) {
        ShiroFriends shiroFriend = createShiroFriend(event);
        return this.saveOrUpdate(shiroFriend);
    }

    private ShiroFriends createShiroFriend(FriendAddNoticeEvent event) {
        Long userId = event.getUserId();
        Long selfId = event.getSelfId();

        Bot bot = botContainer.robots.get(selfId);
        ActionData<StrangerInfoResp> strangerInfo = bot.getStrangerInfo(userId, false);
        String nickname;
        if (strangerInfo != null && strangerInfo.getRetCode() == 0) {
            StrangerInfoResp data = strangerInfo.getData();
            nickname = data.getNickname();
        } else {
            nickname = userId.toString();
        }

        ShiroFriends shiroFriends = new ShiroFriends();
        shiroFriends.setBotId(selfId);
        shiroFriends.setFriendId(userId);
        shiroFriends.setNickname(nickname);
        shiroFriends.setLastUpdated(LocalDateTime.ofEpochSecond(event.getTime() / 1000, 0, java.time.ZoneOffset.UTC));
        return shiroFriends;
    }

    @Override
    public int saveOrUpdateBatchFriends(List<ShiroFriends> list) {
        ShiroFriendsMapper baseMapper = this.getBaseMapper();
        return baseMapper.insertOrUpdateBatch(list);
    }

    @Override
    public boolean saveOrUpdate(ShiroFriends entity) {
        // 1. 根据联合主键查询记录是否存在
        LambdaQueryWrapper<ShiroFriends> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ShiroFriends::getBotId, entity.getBotId());
        queryWrapper.eq(ShiroFriends::getFriendId, entity.getFriendId());

        long count = this.count(queryWrapper);

        if (count > 0) {
            // 2. 如果记录存在，则执行更新操作
            // 注意：update 的 where 条件也需要是联合主键
            return this.update(entity, queryWrapper);
        } else {
            // 3. 如果记录不存在，则执行插入操作
            return this.save(entity);
        }
    }


}




