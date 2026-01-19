package com.github.winefoxbot.core.model.dto.subscription;

import com.github.winefoxbot.core.model.enums.PushTargetType;

/**
 * 订阅目标 DTO
 * 包含了推送所需的所有上下文信息
 */
public record SubscriptionTarget(
        Long botId,             // 必须由哪个 Bot 执行 (严格绑定)
        PushTargetType type,    // 推送类型 (GROUP/PRIVATE)
        Long targetId,          // 目标 ID (群号或QQ号)
        Long mentionUserId      // 需要艾特的用户 ID (可为 null)
) {
    /**
     * 便捷方法：是否需要艾特某人
     */
    public boolean hasMention() {
        return mentionUserId != null && mentionUserId > 0;
    }
}
