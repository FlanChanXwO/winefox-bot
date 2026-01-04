package com.github.winefoxbot.plugins.pixiv.model.dto.bookmark;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PixivApiBody {
    private List<PixivArtwork> works;
    private Integer total;
}
