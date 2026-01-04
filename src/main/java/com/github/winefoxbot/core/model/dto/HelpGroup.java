package com.github.winefoxbot.core.model.dto;

import lombok.Data;

import java.util.List;

@Data
public class HelpGroup {
    /**
     * 插件名称
     */
    private String name;
    /**
     * 插件描述
     */
    private String description;
    /**
     * 插件功能组排序
     */
    private int order = Integer.MAX_VALUE;
    /**
     * 插件图标
     */
    private String icon = "icon/默认图标.png";
    /**
     * 插件功能文档列表
     */
    private List<HelpDoc> documentation;
}
