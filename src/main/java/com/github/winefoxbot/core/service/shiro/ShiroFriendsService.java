package com.github.winefoxbot.core.service.shiro;

import com.github.winefoxbot.core.model.entity.ShiroFriends;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mikuac.shiro.dto.event.notice.FriendAddNoticeEvent;

import java.util.List;

/**
* @author FlanChan
* @description 针对表【shiro_friends(存储每个Bot的好友关系)】的数据库操作Service
* @createDate 2026-01-07 18:23:26
*/
public interface ShiroFriendsService extends IService<ShiroFriends> {

    boolean saveOrUpdateFriend(FriendAddNoticeEvent event);

    int saveOrUpdateBatchFriends(List<ShiroFriends> list);
}
