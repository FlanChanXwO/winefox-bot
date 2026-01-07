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
public enum SetQQAvatarActionPath implements ActionPath {

    FETCH_CUSTOM_FACE("set_qq_avatar"),
    ;

    private final String path;

    SetQQAvatarActionPath(String path) {
        this.path = path;
    }

    @Override
    public String getPath() {
        return this.path;
    }

    @Builder
    public static class SetQQAvatarParams {
        /**
         * 图片路径或链接
         */
        private String file;

        public Map<String,Object> toParamMap() {
            return Map.of(
                    "file", this.file
            );
        }
    }

}
