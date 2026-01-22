package com.github.winefoxbot.core.model.vo.webui.resp;

import java.io.Serializable;

public record StatsRankingResponse(String id, String name, long value) implements Serializable {
}