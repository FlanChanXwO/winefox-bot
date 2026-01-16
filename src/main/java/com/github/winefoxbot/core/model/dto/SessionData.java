package com.github.winefoxbot.core.model.dto;

import lombok.Getter;

import static com.github.winefoxbot.core.constants.SessionConstants.SESSION_TIMEOUT_MS;

/**
 * @author FlanChan
 */
@Getter
public class SessionData<T> {
    private final T data;
    private long lastActiveTime;

    public SessionData(T data) {
        this.data = data;
        this.refresh();
    }

    public void refresh() {
        this.lastActiveTime = System.currentTimeMillis();
    }

    public boolean isExpired() {
        return System.currentTimeMillis() - this.lastActiveTime > SESSION_TIMEOUT_MS;
    }
}