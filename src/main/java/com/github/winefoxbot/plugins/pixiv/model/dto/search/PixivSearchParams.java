package com.github.winefoxbot.plugins.pixiv.model.dto.search;

import lombok.Data;

import java.util.List;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-31-21:02
 */
@Data
public class PixivSearchParams {
    /**
     * 搜索的标签列表，支持多tag
     */
    private List<String> tags;

    /**
     * 当前页码，从 1 开始
     */
    private int pageNo;

    /**
     * 是否搜索 R18 内容
     */
    private boolean r18;
}