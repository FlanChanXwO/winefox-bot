package com.github.winefoxbot.plugins.gscore.model;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
@Data
public class CoreSendRequest {
    @JsonProperty("bot_id")
    private String botId;

    @JsonProperty("bot_self_id")
    private String botSelfId;

    @JsonProperty("target_type")
    private String targetType;

    @JsonProperty("target_id")
    private String targetId;

    private List<MsgNode> content;
    
    @JsonProperty("msg_id")
    private String msgId;
}