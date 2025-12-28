package com.github.winefoxbot.service.pixiv;

import com.github.winefoxbot.model.enums.PixivRankPushMode;
import lombok.Getter;

import java.io.IOException;
import java.util.List;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-10-21:28
 */
public interface PixivRankService {

    void fetchAndPushRank(Long groupId, PixivRankPushMode mode, Content content);

    List<String> getRank(PixivRankPushMode mode, Content content, boolean enabledR18) throws IOException;

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
