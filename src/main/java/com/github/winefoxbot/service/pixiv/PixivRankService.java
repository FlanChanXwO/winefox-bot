package com.github.winefoxbot.service.pixiv;

import lombok.Getter;

import java.io.IOException;
import java.util.List;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-10-21:28
 */
public interface PixivRankService {


    List<String> getRank(Mode mode, Content content, boolean enabledR18) throws IOException;

    @Getter
    enum Mode {
         /**
          * 日榜
          */
        DAILY("daily"),
         /**
          * 周榜
          */
         WEEKLY("weekly"),
         /**
          * 月榜
          */
         MONTHLY("monthly");

        private final String value;

        Mode(String value) {
            this.value = value;
        }
    }

    @Getter
     enum Content {
         /**
          * 全部
          */
        ALL("all"),
         /**
          * 插画
          */
        ILLUST("illust"),
         /**
          * 动图
          */
        UGOIRA("ugoira"),
         /**
          * 漫画
          */
        MANGA("manga");

        private final String value;

        Content(String value) {
            this.value = value;
        }
    }
}
