package com.github.winefoxbot.core.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2026-01-04-0:02
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BroadcastMessageResult {

    private List<Long> successList;

    private List<Long> failedList;

    private boolean allSuccess;


}