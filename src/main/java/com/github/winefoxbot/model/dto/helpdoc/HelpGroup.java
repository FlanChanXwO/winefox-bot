package com.github.winefoxbot.model.dto.helpdoc;

import lombok.Data;
import java.util.List;

@Data
public class HelpGroup {
    private String name;
    private int priority;
    private String icon; // 如果json中为null，这个字段也会是null
    private List<HelpDoc> documentation;
}
