package com.github.winefoxbot.core.service.reply;

import java.io.File;
import java.util.Optional;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2026-01-01-16:53
 */
public interface VoiceReplyService {
    /**
     * 从指定的分类中随机抽取一个语音文件。
     *
     * @param category 分类路径，例如 "poke/passive"
     * @return 如果该分类下有语音文件，则返回一个随机选择的文件；否则返回 Optional.empty()
     */
    Optional<File> drawVoice(String category);
}
