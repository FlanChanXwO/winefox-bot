package com.github.winefoxbot.core.actionpath.napcat;

import com.mikuac.shiro.enums.ActionPath;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;

/**
 * set_qq_avatar - 设置QQ头像
 * @author FlanChan (badapple495@outlook.com)
 * @since 2026-01-07-17:50
 */
@Getter
public enum SetSelfLongNickActionPath implements ActionPath {

    SET_SELF_LONGNICK("set_self_longnick"),
    ;

    private final String path;

    SetSelfLongNickActionPath(String path) {
        this.path = path;
    }

    @Override
    public String getPath() {
        return this.path;
    }

    @Builder
    public static class SetSelfLongNickParams {
        /**
         * 签名
         */
        private String longNick;

        public Map<String,Object> toParamMap() {
            return Map.of("longNick", longNick);
        }
    }

}
