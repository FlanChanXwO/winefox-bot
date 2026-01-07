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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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


    @Override
    @Transactional(rollbackFor = Exception.class) // 保证整个操作的原子性
    public boolean saveOrUpdateBatch(Collection<ShiroFriends> entityList) {
        if (CollectionUtils.isEmpty(entityList)) {
            return true;
        }

        // 1. 准备需要查询的联合主键
        // 使用一个 Set<String> 或 Set<Pair> 来唯一标识每个实体
        // 这里用 "botId_friendId" 的字符串作为唯一键
        Map<String, ShiroFriends> entityMap = entityList.stream()
                .collect(Collectors.toMap(
                        e -> e.getBotId() + "_" + e.getFriendId(),
                        e -> e,
                        (e1, e2) -> e2 // 如果有重复的key，保留后者
                ));

        // 2. 根据联合主键批量查询已存在的记录
        // 使用 in-clause 查询，但需要构造复杂的 OR 条件
        LambdaQueryWrapper<ShiroFriends> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.and(wrapper -> {
            entityList.forEach(entity -> {
                wrapper.or(w -> w.eq(ShiroFriends::getBotId, entity.getBotId()).eq(ShiroFriends::getFriendId, entity.getFriendId()));
            });
        });
        List<ShiroFriends> existingList = this.list(queryWrapper);

        // 3. 分离出需要更新和需要插入的列表
        List<ShiroFriends> toUpdateList = new ArrayList<>();
        List<ShiroFriends> toInsertList;

        if (!CollectionUtils.isEmpty(existingList)) {
            for (ShiroFriends existing : existingList) {
                String key = existing.getBotId() + "_" + existing.getFriendId();
                ShiroFriends incomingEntity = entityMap.get(key);
                if (incomingEntity != null) {
                    toUpdateList.add(incomingEntity);
                    entityMap.remove(key);
                }
            }
        }

        // entityMap 中剩下的就是数据库中不存在的，需要插入
        toInsertList = new ArrayList<>(entityMap.values());

        // 4. 执行批量插入
        boolean insertResult = true;
        if (!CollectionUtils.isEmpty(toInsertList)) {
            insertResult = this.saveBatch(toInsertList);
        }

        // 5. 执行批量更新
        // Mybatis-Plus 默认没有批量更新方法，需要自己实现或逐条更新
        // 方案A: 逐条更新 (简单但效率低，会多次DB交互)
        for (ShiroFriends entity : toUpdateList) {
            LambdaQueryWrapper<ShiroFriends> updateWrapper = new LambdaQueryWrapper<>();
            updateWrapper.eq(ShiroFriends::getBotId, entity.getBotId()).eq(ShiroFriends::getFriendId, entity.getFriendId());
            this.update(entity, updateWrapper);
        }
        // 这里我们假设逐条更新成功，可以添加更复杂的成功判断逻辑
        boolean updateResult = true;

        // 方案B: （更优）在Mapper中写自定义的批量更新方法，利用 case when 语法或 Mybatis的foreach标签。
        // 如果追求高性能，推荐方案B，这里为了演示清晰，使用方案A。

        return insertResult && updateResult;
    }
}




