package com.github.winefoxbot.core.service.subscription;

import com.baomidou.mybatisplus.extension.service.IService;
import com.github.winefoxbot.core.model.dto.SubscriptionTarget;
import com.github.winefoxbot.core.model.entity.ShiroEventSubscription;

import java.util.List;

/**
* @author FlanChan
* @description 针对表【shiro_event_subscription】的数据库操作Service
* @createDate 2026-01-19 12:23:03
*/
public interface ShiroEventSubscriptionService extends IService<ShiroEventSubscription> {
    /**
     * 查所有 (群 + 私聊)
     */
    List<SubscriptionTarget> findTargets(String eventType, String eventKey);

    /**
     * 【新增】只查群组订阅
     * 返回结果中的 type 永远是 GROUP
     */
    List<SubscriptionTarget> findGroupTargets(String eventType, String eventKey);

    /**
     * 【新增】只查私聊订阅
     * 返回结果中的 type 永远是 PRIVATE
     */
    List<SubscriptionTarget> findPrivateTargets(String eventType, String eventKey);
}
