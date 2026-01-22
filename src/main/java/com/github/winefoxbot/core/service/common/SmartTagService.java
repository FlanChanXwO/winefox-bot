package com.github.winefoxbot.core.service.common;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;
import com.hankcs.hanlp.HanLP;
import com.hankcs.hanlp.seg.common.Term;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 智能搜素标签服务
 */
@Service
public class SmartTagService {

    // 内存中的白名单 (不再是 static，由 Spring 维护单例)
    private final Set<String> protectedIps = new HashSet<>();

    // 分隔符正则
    private static final String SEPARATOR_REGEX = "[\\s,，|;；、]+";

    /**
     * 提供给外部添加词汇的方法 (供初始化器调用)
     */
    public void addProtectedWord(String word) {
        if (StrUtil.isNotBlank(word)) {
            this.protectedIps.add(word);
        }
    }

    /**
     * 获取白名单数量 (用于监控)
     */
    public int getDictSize() {
        return this.protectedIps.size();
    }

    /**
     * 核心业务方法：获取搜索 Tags
     */
    public List<String> getSearchTags(String text) {
        if (StrUtil.isBlank(text)) return Collections.emptyList();

        // 1. 物理切割 + 清洗
        List<String> rawParts = Arrays.stream(text.split(SEPARATOR_REGEX))
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toList());

        if (rawParts.isEmpty()) return Collections.emptyList();

        // 2. 分支 A: 用户手动使用了分隔符
        if (rawParts.size() > 1) {
            return rawParts;
        }

        // 3. 分支 B: 智能分析
        return analyzeSmartSegment(rawParts.get(0));
    }

    private List<String> analyzeSmartSegment(String text) {
        // 白名单检查 (忽略大小写)
        if (protectedIps.contains(text) || protectedIps.contains(text.toLowerCase())) {
            return List.of(text);
        }

        // HanLP 分词
        List<Term> terms = HanLP.segment(text);
        List<String> result = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();

        for (Term term : terms) {
            String word = term.word;
            // 过滤纯符号
            if (ReUtil.isMatch("[\\p{P}\\p{S}]+", word)) continue;

            if (word.length() == 1) {
                buffer.append(word);
            } else {
                if (!buffer.isEmpty()) {
                    if (buffer.length() >= 2) result.add(buffer.toString());
                    buffer.setLength(0);
                }
                result.add(word);
            }
        }
        if (!buffer.isEmpty() && buffer.length() >= 2) {
            result.add(buffer.toString());
        }

        if (result.isEmpty() || (result.size() == 1 && result.get(0).equals(text))) {
            return List.of(text);
        }
        return result;
    }
}
