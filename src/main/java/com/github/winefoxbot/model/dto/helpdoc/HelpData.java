package com.github.winefoxbot.model.dto.helpdoc;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.List;

@Data
public class HelpData {
    @JsonIgnore
    private String bottomBackground = "image/bottom.jpg";
    
    @JsonIgnore
    private String defaultIcon = "icon/默认图标.png";

    private List<HelpGroup> groups;
}
