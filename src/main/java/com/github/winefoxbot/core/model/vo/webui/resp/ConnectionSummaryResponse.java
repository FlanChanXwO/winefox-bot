package com.github.winefoxbot.core.model.vo.webui.resp;

import java.time.LocalDateTime;

public record ConnectionSummaryResponse(
        long totalLoginCount,
        String connectionDuration,
        LocalDateTime connectionDate
    ) {}
