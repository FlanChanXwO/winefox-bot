package com.github.winefoxbot.core.model.vo.webui.resp;

public record InvokeSummaryResponse(
        long total,
        long day,
        long week,
        long month,
        long year
) {
}