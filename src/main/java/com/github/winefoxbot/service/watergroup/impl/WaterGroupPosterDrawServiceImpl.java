package com.github.winefoxbot.service.watergroup.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.winefoxbot.model.dto.WaterGroupMemberStat;
import com.github.winefoxbot.model.entity.ShiroGroupMember;
import com.github.winefoxbot.model.entity.ShiroUser;
import com.github.winefoxbot.model.entity.WaterGroupMessageStat;
import com.github.winefoxbot.service.shiro.ShiroGroupMembersService;
import com.github.winefoxbot.service.shiro.ShiroUsersService;
import com.github.winefoxbot.service.watergroup.WaterGroupPosterDrawService;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.ScreenshotScale;
import com.microsoft.playwright.options.WaitUntilState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StreamUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class WaterGroupPosterDrawServiceImpl implements WaterGroupPosterDrawService {

    private final Browser browser;
    private final ShiroGroupMembersService shiroGroupMembersService;
    private final ShiroUsersService usersService;
    private final ResourceLoader resourceLoader;

    @Override
    public File drawPoster(List<WaterGroupMessageStat> stats) throws IOException {
        if (CollectionUtils.isEmpty(stats)) {
            // 如果没有统计数据，可以返回一个表示空状态的图片或直接返回
            return null;
        }

        // 计算总发言数
        long totalMsgCount = stats.stream()
                .mapToLong(WaterGroupMessageStat::getMsgCount)
                .sum();

        // 排序
        stats.sort(Comparator.comparingInt(WaterGroupMessageStat::getMsgCount).reversed());

        // ======================= 性能优化开始 =======================
        List<Long> userIds = stats.stream().map(WaterGroupMessageStat::getUserId).distinct().toList();
        Long groupId = stats.isEmpty() ? null : stats.get(0).getGroupId();

        Map<Long, ShiroUser> userMap = Collections.emptyMap();
        Map<Long, ShiroGroupMember> memberMap = Collections.emptyMap();

        if (!userIds.isEmpty() && groupId != null) {
            // 1. 一次性查询所有用户信息
            userMap = usersService.list(new LambdaQueryWrapper<ShiroUser>()
                            .in(ShiroUser::getUserId, userIds))
                    .stream()
                    .collect(Collectors.toMap(ShiroUser::getUserId, Function.identity()));

            // 2. 一次性查询所有群成员信息
            memberMap = shiroGroupMembersService.list(new LambdaQueryWrapper<ShiroGroupMember>()
                            .eq(ShiroGroupMember::getGroupId, groupId)
                            .in(ShiroGroupMember::getUserId, userIds))
                    .stream()
                    .collect(Collectors.toMap(ShiroGroupMember::getUserId, Function.identity()));
        }

        final Map<Long, ShiroUser> finalUserMap = userMap;
        final Map<Long, ShiroGroupMember> finalMemberMap = memberMap;

        List<WaterGroupMemberStat> statList = stats.stream()
                .map(e -> {
                    WaterGroupMemberStat stat = new WaterGroupMemberStat();
                    BeanUtil.copyProperties(e, stat);

                    ShiroGroupMember member = finalMemberMap.get(e.getUserId());
                    if (member != null) {
                        stat.setNickname(member.getMemberNickname());
                    } else {
                        // 如果找不到群成员信息，可以设置一个默认值
                        stat.setNickname("未知成员");
                    }

                    ShiroUser user = finalUserMap.get(e.getUserId());
                    if (user != null) {
                        stat.setAvtarUrl(user.getAvatarUrl());
                    } else {
                        // 设置一个默认头像URL
                        stat.setAvtarUrl("https://via.placeholder.com/64");
                    }
                    return stat;
                }).toList();


        StringBuilder rankHtml = new StringBuilder();
        for (int i = 0; i < statList.size(); i++) {
            rankHtml.append(
                    buildRankItemHtml(i + 1, statList.get(i), totalMsgCount)
            );
        }

        // 读取模板
        String template;
        try {
            // 1. 使用 resourceLoader 获取资源对象
            Resource resource = resourceLoader.getResource("classpath:templates/water_group/water_group_poster.html");
            // 2. 从资源对象获取输入流 (InputStream)
            try (InputStream inputStream = resource.getInputStream()) {
                // 3. 使用工具类将输入流复制到字符串，并指定编码
                template = StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new RuntimeException("读取海报模板文件失败", e);
        }

        String html = template
                .replace("{{time}}", LocalDateTime.now().format(
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
                .replace("{{rank_list}}", rankHtml.toString())
                .replace("{{generator}}", buildGenerator(statList));

        return renderByPlaywright(html);
    }

    private File renderByPlaywright(String html) {
        Browser.NewPageOptions pageOptions = new Browser.NewPageOptions();
        pageOptions.setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
        try (Page page = browser.newPage(pageOptions)) {
            page.setViewportSize(800, 100); // 初始视口可以小一点，后面会根据内容自适应
            page.setContent(html, new Page.SetContentOptions()
                    .setWaitUntil(WaitUntilState.NETWORKIDLE));
            // 获取 poster 元素的高度，并设置为视口高度，确保截图完整
            int height = (int) page.locator(".poster").boundingBox().height;
            page.setViewportSize(800, height);

            File out = new File("water_group_rank.png");
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(out.toPath())
                    .setFullPage(true) // 使用fullPage确保内容截全
                    .setScale(ScreenshotScale.CSS));

            return out;
        }
    }


    private String buildGenerator(List<WaterGroupMemberStat> stats) {
        if (stats == null || stats.isEmpty()) {
            return "本群";
        }

        // 最多显示 4 个，防止太长
        int limit = Math.min(stats.size(), 4);

        return stats.stream()
                .limit(limit)
                .map(WaterGroupMemberStat::getNickname)
                .filter(Objects::nonNull)
                .filter(name -> !name.isBlank())
                .collect(Collectors.joining("、")) + " 等"; // 加上 "等" 更自然
    }


    private String buildRankItemHtml(
            int rank,
            WaterGroupMemberStat stat,
            long totalCount
    ) {
        double percent = totalCount == 0
                ? 0
                : stat.getMsgCount() * 100.0 / totalCount;

        String barColor;
        String rankClass = "";  // 新增：用于存储特殊的CSS类
        String rankIcon = "";   // 新增：用于存储皇冠图标HTML

        switch (rank) {
            case 1:
                barColor = "linear-gradient(90deg, #FFB86C, #FF79C6)";
                rankClass = "rank-1";
                // 使用SVG图标，因为它清晰且易于嵌入
                rankIcon = "<span class='crown gold'>👑</span>";
                break;
            case 2:
                barColor = "linear-gradient(90deg, #8BE9FD, #50FA7B)";
                rankClass = "rank-2";
                rankIcon = "<span class='crown silver'>🥈</span>"; // Emoji也可以，但SVG更可控
                break;
            case 3:
                barColor = "linear-gradient(90deg, #BD93F9, #FF79C6)";
                rankClass = "rank-3";
                rankIcon = "<span class='crown bronze'>🥉</span>";
                break;
            default:
                barColor = "#44475A";
                // 其他排名没有特殊类和图标
                break;
        }

        return """
                <div class="rank-item %s">
                  <div class="rank-number">%d</div>
                  <div class="avatar">
                    <img src="%s" alt="avatar"/>
                  </div>
                  <div class="info">
                    <div class="name">%s %s</div>
                    <div class="count">发言次数: %d</div>
                  </div>
                  <div class="progress-container">
                    <div class="progress-bar-bg">
                        <div class="progress-bar-fg" style="width: %.2f%%; background: %s;"></div>
                    </div>
                    <div class="percent-text">%.2f%%</div>
                  </div>
                </div>
                """
                .formatted(
                        rankClass,          // 应用特殊CSS类
                        rank,
                        stat.getAvtarUrl(),
                        stat.getNickname(),
                        rankIcon,           // 在名字后面添加皇冠
                        stat.getMsgCount(),
                        percent,
                        barColor,
                        percent
                );
    }

}
