package com.github.winefoxbot.core.service.subscription.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.winefoxbot.core.mapper.ShiroEventSubscriptionMapper;
import com.github.winefoxbot.core.model.dto.SubscriptionTarget;
import com.github.winefoxbot.core.model.entity.ShiroEventSubscription;
import com.github.winefoxbot.core.model.enums.PushTargetType;
import com.github.winefoxbot.core.service.subscription.ShiroEventSubscriptionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * @author FlanChan
 */
@Service
@Slf4j
public class ShiroEventSubscriptionServiceImpl extends ServiceImpl<ShiroEventSubscriptionMapper, ShiroEventSubscription>
        implements ShiroEventSubscriptionService {

    @Override
    public List<SubscriptionTarget> findTargets(String eventType, String eventKey) {
        return queryTargets(eventType, eventKey, null);
    }

    @Override
    public List<SubscriptionTarget> findGroupTargets(String eventType, String eventKey) {
        return queryTargets(eventType, eventKey, PushTargetType.GROUP);
    }

    @Override
    public List<SubscriptionTarget> findPrivateTargets(String eventType, String eventKey) {
        return queryTargets(eventType, eventKey, PushTargetType.PRIVATE);
    }

    /**
     * 核心查询逻辑
     *
     * @param limitType 如果不为null，则只查询特定类型的订阅；为null则查询所有
     */
    private List<SubscriptionTarget> queryTargets(String eventType, String eventKey, PushTargetType limitType) {
        if (eventType == null) {
            return Collections.emptyList();
        }

        // 1. 构建查询
        LambdaQueryWrapper<ShiroEventSubscription> wrapper = new LambdaQueryWrapper<ShiroEventSubscription>()
                .eq(ShiroEventSubscription::getEventType, eventType);

        // Key 过滤
        if (eventKey != null) {
            wrapper.eq(ShiroEventSubscription::getEventKey, eventKey);
        } else {
            wrapper.isNull(ShiroEventSubscription::getEventKey);
        }

        // 类型过滤 (数据库层面过滤，假设数据库存的是枚举的 name，如 "GROUP")
        if (limitType != null) {
            // 注意：这里假设数据库存的是大写，如果存的是 int 或小写，请根据实际情况调整 .name()
            wrapper.eq(ShiroEventSubscription::getTargetType, limitType.name());
        }

        // 2. 执行查询并转换
        return this.list(wrapper).stream()
                .map(this::convertEntityToTarget)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 实体转 DTO
     */
    private SubscriptionTarget convertEntityToTarget(ShiroEventSubscription sub) {
        try {
            // 容错处理：数据库里的字符串转枚举
            PushTargetType type = PushTargetType.valueOf(sub.getTargetType().toUpperCase());

            return new SubscriptionTarget(
                    sub.getBotId(),
                    type,
                    sub.getTargetId(),
                    sub.getMentionUserId()
            );
        } catch (Exception e) {
            log.warn("忽略无效订阅: ID={}, Type={}", sub.getId(), sub.getTargetType());
            return null;
        }
    }
}
