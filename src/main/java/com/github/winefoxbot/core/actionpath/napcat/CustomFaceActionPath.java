package com.github.winefoxbot.core.actionpath.napcat;

import com.mikuac.shiro.enums.ActionPath;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;

/**
 * fetch_custom_face - 获取自定义表情
 * @author FlanChan (badapple495@outlook.com)
 * @since 2026-01-07-17:50
 */
@Getter
public enum CustomFaceActionPath implements ActionPath {

    FETCH_CUSTOM_FACE("fetch_custom_face"),
    ;

    private final String path;

    CustomFaceActionPath(String path) {
        this.path = path;
    }

    @Override
    public String getPath() {
        return this.path;
    }

    @Builder
    public static class FetchCustomFaceParams {
        /**
         * 图片路径或链接
         */
        private Integer count;

        public Map<String,Object> toParamMap() {
            return Map.of(
                    "count", this.count
            );
        }
    }

}
