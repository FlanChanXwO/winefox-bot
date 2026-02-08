package com.github.winefoxbot.plugins.gscore.model;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MsgNode {
    private String type;
    private Object data;
}