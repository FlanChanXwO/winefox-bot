package com.github.winefoxbot.service.reply.impl;

import com.github.winefoxbot.config.reply.WinefoxBotVoiceReplyConfig;
import com.github.winefoxbot.service.reply.VoiceReplyService;
import com.github.winefoxbot.utils.LotteryUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.*;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2026-01-01-16:53
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class VoiceReplyServiceImpl implements VoiceReplyService {
    private final WinefoxBotVoiceReplyConfig voiceReplyConfig;

    @Override
    public Optional<File> drawVoice(String category) {
        Map<String, List<File>> voiceMap = voiceReplyConfig.getVoiceMap();
        if (voiceMap == null) {
            log.warn("语音配置中的 voiceMap 为空，无法抽取语音，返回空。");
            return Optional.empty();
        }

        List<File> voiceFiles = voiceMap.get(category);

        if (voiceFiles == null || voiceFiles.isEmpty()) {
            // 如果该分类下没有语音，返回空
            return Optional.empty();
        }

        // 使用你的 LotteryUtils 进行抽奖
        // 1. 构建奖品池
        // 因为每个语音的概率是均等的，所以我们可以把所有语音放在一个List里，并给这个List设置100%的概率。
        // LotteryUtils.draw 方法内部会从这个List中再随机抽取一个。
        Map<Double, List<Object>> prizeMap = new HashMap<>();

        // 将 List<File> 转换为 List<Object>
        List<Object> prizeList = new ArrayList<>(voiceFiles);

        // 设置概率为 1.0 (100%)，表示必定从这个奖品列表中抽取
        prizeMap.put(1.0, prizeList);

        try {
            // 2. 调用抽奖工具
            Object result = LotteryUtils.draw(prizeMap);

            if (result instanceof File) {
                File chosenFile = (File) result;
                log.debug("通过 LotteryUtils 从分类 '{}' 中抽中语音: {}", category, chosenFile.getName());
                return Optional.of(chosenFile);
            } else {
                // 理论上不应该发生，除非 prizeMap 为空，但我们已经做了检查
                log.warn("LotteryUtils 返回了非 File 类型或 null，返回空。");
                return Optional.empty();
            }
        } catch (Exception e) {
            log.error("使用 LotteryUtils 抽奖时发生异常", e);
            return Optional.empty();
        }
    }
}
