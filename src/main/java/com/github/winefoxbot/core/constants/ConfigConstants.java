package com.github.winefoxbot.core.constants;

/**
 * 配置键常量
 * @author FlanChan (badapple495@outlook.com)
 * @since 2026-01-06-1:01
 */
public final class ConfigConstants {
    private ConfigConstants() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static final class AdultContent {
        private AdultContent() {
            throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
        }

        public static final String GROUP_ADULT_CONTENT = "福利内容";
        /**
         * 配置项：福利内容模式。
         * 推荐使用字符串常量作为值。
         */
        public static final String SETU_CONTENT_MODE = "setu.content.mode";
        public static final String MODE_SFW = "sfw";
        public static final String MODE_R18 = "r18";
        public static final String MODE_MIX = "mix";
        /**
         * 配置项：是否自动撤回R18消息（PDF文件）。
         * 仅在群聊中生效。
         */
        public static final String ADULT_AUTO_REVOKE_ENABLED = "adult.revoke.auto_enabled";
        /**
         * 配置项：R18消息（PDF文件）自动撤回的延迟时间（单位：秒）。
         * 仅在群聊中生效。
         */
        public static final String ADULT_REVOKE_DELAY_SECONDS = "adult.revoke.delay_seconds";
    }


}
