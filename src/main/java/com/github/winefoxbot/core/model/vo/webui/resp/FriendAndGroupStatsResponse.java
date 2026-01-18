package com.github.winefoxbot.core.model.vo.webui.resp;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2026-01-18-21:20
 */
public record FriendAndGroupStatsResponse(
        long friendCount,
        long groupCount
) {}