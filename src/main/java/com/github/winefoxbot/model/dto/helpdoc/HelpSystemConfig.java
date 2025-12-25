package com.github.winefoxbot.model.dto.helpdoc;// HelpSystemConfig.java

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import java.util.List;

/**
 * 代表整个帮助系统配置的顶层类，
 * 包含了文档内容和它们的显示顺序。
 */
@Data
public class HelpSystemConfig {
    @JsonAlias("group_order")
    private List<String> groupOrder;
    @JsonAlias("documentation")
    private List<HelpDoc> documentation; // HelpDoc 是您已有的文档条目类
}
