package com.github.winefoxbot.plugins.watergroup.service;

import com.github.winefoxbot.plugins.watergroup.model.entity.WaterGroupMessageStat;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-16-1:27
 */
public interface WaterGroupPosterDrawService {


    File drawPoster(List<WaterGroupMessageStat> stats) throws IOException;
}
