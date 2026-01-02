package com.github.winefoxbot.model.dto.pixiv;

import com.github.winefoxbot.model.enums.PixivArtworkType;
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
public class PixivDetail {
    private String pid;
    private String title;
    private String uid;
    private String userName;
    private String description;
    private Boolean isR18;
    private PixivArtworkType type;
    private List<String> tags = new ArrayList<>();
}