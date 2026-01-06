package com.github.winefoxbot.core.model.entity;

import cn.hutool.json.JSONArray;
import com.baomidou.mybatisplus.annotation.*;
import com.github.winefoxbot.core.model.enums.MessageDirection;
import com.github.winefoxbot.core.model.enums.MessageType;
import com.github.winefoxbot.core.model.type.PGJsonTypeHandler;
import com.google.gson.JsonArray;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Shiro 訊息實體
 */
@Data
@TableName(value = "shiro_messages", autoResultMap = true)
public class ShiroMessage {

    /**
     * 訊息的唯一標識符 (自動遞增)
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /**
     * 訊息 ID
     */
    private Long messageId;

    /**
     * 訊息時間
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime time;

    /**
     * 機器人自身 ID
     */
    private Long selfId;

    /**
     * 訊息類型 (private/group)
     */
    private MessageType messageType;

    /**
     * 訊息方向 (message/message_sent)
     */
    private MessageDirection direction;

    /**
     * 發送者 ID
     */
    private Long userId;

    /**
     * 会话 ID (如果是羣組訊息)
     */
    private Long sessionId;

    /**
     * 原始訊息內容 (JSON 格式)
     * 注意：需要配置MyBatis Plus的JacksonTypeHandler来自动处理JSON字符串和对象之间的转换
     */
    @TableField(typeHandler = PGJsonTypeHandler.class)
    private JSONArray message;

    /**
     * 纯文本訊息内容
     */
    private String plainText;

}