package com.github.winefoxbot.model.dto.helpdoc;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
public class HelpData {
    @JsonProperty("top-background")
    private String topBackground;

    @JsonProperty("bottom-background")
    private String bottomBackground;
    
    @JsonProperty("default-icon")
    private String defaultIcon;

    private List<HelpGroup> groups;
}
