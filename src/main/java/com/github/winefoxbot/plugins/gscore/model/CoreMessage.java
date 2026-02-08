package com.github.winefoxbot.plugins.gscore.model;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CoreMessage {
    @JsonProperty("bot_id")
    private String botId;

    @JsonProperty("bot_self_id")
    private String botSelfId;

    @JsonProperty("msg_id")
    private String msgId;

    @JsonProperty("user_type")
    private String userType;

    @JsonProperty("group_id")
    private String groupId;

    @JsonProperty("user_id")
    private String userId;

    private Object sender;

    @JsonProperty("user_pm")
    private int userPm;

    private List<MsgNode> content;
}