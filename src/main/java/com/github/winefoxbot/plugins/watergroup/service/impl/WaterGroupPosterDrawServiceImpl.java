package com.github.winefoxbot.plugins.watergroup.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.winefoxbot.core.annotation.common.Retry;
import com.github.winefoxbot.core.context.BotContext;
import com.github.winefoxbot.core.model.entity.ShiroGroupMember;
import com.github.winefoxbot.core.model.entity.ShiroUser;
import com.github.winefoxbot.core.service.shiro.ShiroGroupMembersService;
import com.github.winefoxbot.core.service.shiro.ShiroUsersService;
import com.github.winefoxbot.plugins.watergroup.model.dto.WaterGroupMemberStat;
import com.github.winefoxbot.plugins.watergroup.model.entity.WaterGroupMessageStat;
import com.github.winefoxbot.plugins.watergroup.service.WaterGroupPosterDrawService;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.ScreenshotScale;
import com.microsoft.playwright.options.WaitUntilState;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author FlanChan
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WaterGroupPosterDrawServiceImpl implements WaterGroupPosterDrawService {

    private final Browser browser;
    private final ShiroGroupMembersService shiroGroupMembersService;
    private final ShiroUsersService usersService;
    private final TemplateEngine templateEngine; // 注入模板引擎

    @Override
    public File drawPoster(List<WaterGroupMessageStat> stats) {
        if (CollectionUtils.isEmpty(stats)) {
            return null;
        }

        // 1. 数据准备：计算总数、排序
        long totalMsgCount = stats.stream().mapToLong(WaterGroupMessageStat::getMsgCount).sum();
        stats.sort(Comparator.comparingInt(WaterGroupMessageStat::getMsgCount).reversed());

        // 2. 批量查询用户信息
        List<Long> userIds = stats.stream().map(WaterGroupMessageStat::getUserId).distinct().toList();
        Long groupId = stats.getFirst().getGroupId();

        Map<Long, ShiroUser> userMap = Collections.emptyMap();
        Map<Long, ShiroGroupMember> memberMap = Collections.emptyMap();

        if (!userIds.isEmpty()) {
            userMap = usersService.list(new LambdaQueryWrapper<ShiroUser>().in(ShiroUser::getUserId, userIds))
                    .stream().collect(Collectors.toMap(ShiroUser::getUserId, Function.identity()));
            memberMap = shiroGroupMembersService.list(new LambdaQueryWrapper<ShiroGroupMember>()
                            .eq(ShiroGroupMember::getGroupId, groupId).in(ShiroGroupMember::getUserId, userIds))
                    .stream().collect(Collectors.toMap(ShiroGroupMember::getUserId, Function.identity()));
        }

        final Map<Long, ShiroUser> finalUserMap = userMap;
        final Map<Long, ShiroGroupMember> finalMemberMap = memberMap;

        // 3. 组装视图数据 (DTO)
        List<Map<String, Object>> rankList = new ArrayList<>();
        for (int i = 0; i < stats.size(); i++) {
            WaterGroupMessageStat stat = stats.get(i);
            int rank = i + 1;

            ShiroGroupMember member = finalMemberMap.get(stat.getUserId());
            String nickname = (member != null) ? member.getMemberNickname() : "未知成员";

            ShiroUser user = finalUserMap.get(stat.getUserId());
            String avatarUrl = (user != null) ? user.getAvatarUrl() : "";

            double percent = totalMsgCount == 0 ? 0 : stat.getMsgCount() * 100.0 / totalMsgCount;

            // 纯粹的数据 Map，不包含任何 HTML 标签
            Map<String, Object> item = new HashMap<>();
            item.put("rank", rank);
            item.put("nickname", nickname);
            item.put("avatarUrl", avatarUrl);
            item.put("msgCount", stat.getMsgCount());
            item.put("percent", percent); // 让前端格式化或者这里传字符串都可以

            rankList.add(item);
        }

        // 4. 构建 Thymeleaf Context
        Context context = new Context();
        context.setVariable("time", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        context.setVariable("rankList", rankList);
        context.setVariable("generator", buildGeneratorName(rankList));

        // 5. 渲染
        String html = templateEngine.process("water_group/main", context);
        return renderByPlaywright(html);
    }

    private String buildGeneratorName(List<Map<String, Object>> rankList) {
        if (rankList.isEmpty()) return "本群";
        return rankList.stream()
                .limit(Math.min(rankList.size(), 4))
                .map(m -> (String) m.get("nickname"))
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .collect(Collectors.joining("、")) + " 等";
    }

    @Retry(retryOn = RuntimeException.class)
    private File renderByPlaywright(String html) {
        Browser.NewPageOptions pageOptions = new Browser.NewPageOptions();
        pageOptions.setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
        try (Page page = browser.newPage(pageOptions)) {
            page.setViewportSize(800, 100);
            page.setContent(html, new Page.SetContentOptions().setWaitUntil(WaitUntilState.NETWORKIDLE).setTimeout(30000));
            int height = (int) page.locator(".poster").boundingBox().height;
            page.setViewportSize(800, height);
            GroupMessageEvent messageEvent = (GroupMessageEvent) BotContext.CURRENT_MESSAGE_EVENT.get();
            File out = new File("water_group_rank_%s.png".formatted(messageEvent.getGroupId()));
            page.screenshot(new Page.ScreenshotOptions().setPath(out.toPath()).setFullPage(true).setScale(ScreenshotScale.CSS));
            return out;
        }
    }
}
