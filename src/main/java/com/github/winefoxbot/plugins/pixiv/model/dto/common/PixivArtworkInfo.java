package com.github.winefoxbot.plugins.pixiv.model.dto.common;

import com.github.winefoxbot.plugins.pixiv.model.enums.PixivArtworkType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-16-21:12
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PixivArtworkInfo {
    private String pid;
    private String title;
    private String uid;
    private String userName;
    private String description;
    private Boolean isR18;
    private PixivArtworkType type;
    private List<String> tags = new ArrayList<>();
}