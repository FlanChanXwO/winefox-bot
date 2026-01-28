package com.github.winefoxbot.plugins.deerpipe.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.winefoxbot.core.config.playwright.PlaywrightConfig;
import com.github.winefoxbot.core.utils.DynamicResourceLoader;
import com.github.winefoxbot.plugins.deerpipe.mapper.DeerRecordMapper;
import com.github.winefoxbot.plugins.deerpipe.mapper.DeerUserConfigMapper;
import com.github.winefoxbot.plugins.deerpipe.model.dto.*;
import com.github.winefoxbot.plugins.deerpipe.model.entity.DeerRecord;
import com.github.winefoxbot.plugins.deerpipe.model.entity.DeerUserConfig;
import com.github.winefoxbot.plugins.deerpipe.service.DeerService;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.ScreenshotType;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author FlanChan
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DeerServiceImpl extends ServiceImpl<DeerRecordMapper, DeerRecord>
        implements DeerService {
    private final DeerUserConfigMapper userConfigMapper;
    private final TemplateEngine templateEngine;
    private final Browser browser;
    private final OkHttpClient httpClient;
    private final PlaywrightConfig playwrightConfig;

    // 资源路径常量 (保持不变)
    private static final String CALENDAR_CSS_PATH = "templates/deerpipe/res/css/calendar.css";
    private static final String BATCH_REPORT_CSS_PATH = "templates/deerpipe/res/css/batch_report.css";
    private static final String IMG_CHECK_PATH = "templates/deerpipe/res/images/check.png";
    private static final String IMG_PIPE_PATH = "templates/deerpipe/res/images/deerpipe.png";
    private static final List<String> CHARCTER_PICTURE_PATHS = List.of(
            "templates/deerpipe/res/images/character_1.png","templates/deerpipe/res/images/character_2.png",
            "templates/deerpipe/res/images/character_3.png","templates/deerpipe/res/images/character_4.png",
            "templates/deerpipe/res/images/character_5.png","templates/deerpipe/res/images/character_6.png",
            "templates/deerpipe/res/images/character_7.png", "templates/deerpipe/res/images/character_8.png",
            "templates/deerpipe/res/images/character_9.png", "templates/deerpipe/res/images/character_10.png",
            "templates/deerpipe/res/images/character_11.png"
    );
    private static final String TEMPLATE_CALENDAR = "deerpipe/calendar";
    private static final String TEMPLATE_BATCH_REPORT = "deerpipe/batch_report";

    /**
     * 自己的普通签到
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public byte[] attend(Long userId, String avatarUrl) {
        LocalDate now = LocalDate.now();
        saveOrUpdateRecordAndGetCount(userId, now);
        return generateCalendarImage(userId, now, avatarUrl);
    }

    /**
     * 帮别人签到（单人），含权限检查
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public byte[] attendByOther(Long targetUserId, String targetNickname, String avatarUrl) {
        if (!isHelpAllowed(targetUserId)) {
            throw new RuntimeException("该用户已开启防帮鹿护盾！");
        }
        return attend(targetUserId, avatarUrl);
    }

    /**
     * 批量签到入口，含权限检查
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public byte[] batchAttendAndRender(List<BatchTarget> targets) {
        List<AttendResult> results = new ArrayList<>();
        LocalDate now = LocalDate.now();

        // 1. 获取所有目标的配置
        List<Long> userIds = targets.stream().map(BatchTarget::userId).toList();
        Map<Long, Boolean> allowHelpMap = getUserAllowHelpMap(userIds);

        for (BatchTarget target : targets) {
            boolean allowed = allowHelpMap.getOrDefault(target.userId(), true);
            if (allowed) {
                results.add(doAttend(target.userId(), target.nickname(), now));
            } else {
                // 如果不允许被帮，记录一个特殊状态 (count = -1 表示被拒绝/失败)
                results.add(new AttendResult(target.userId(), target.nickname(), false, -1));
            }
        }

        // 渲染批量报告图片
        return generateBatchReportImage(results);
    }

    private AttendResult doAttend(Long userId, String nickname, LocalDate now) {
        int currentCount = saveOrUpdateRecordAndGetCount(userId, now);
        boolean isNewDay = (currentCount == 1);
        return new AttendResult(userId, nickname, isNewDay, currentCount);
    }

    /**
     * 补签逻辑
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public AttendanceResult attendPast(Long userId, int day, String avatarUrl) {
        LocalDate now = LocalDate.now();

        // 1. 基础校验
        if (day < 1 || day >= now.getDayOfMonth()) {
            return new AttendanceResult(false, "不是合法的补鹿日期捏（只能补本月且早于今天的日期）", null);
        }

        // 2. 检查是否今天已经使用过补签机会
        DeerUserConfig config = getOrCreateUserConfig(userId);
        if (now.equals(config.getLastRetroDate())) {
            // 为了用户体验，虽然失败了，还是返回日历图看一眼
            byte[] img = generateCalendarImage(userId, now, avatarUrl);
            return new AttendanceResult(false, "你今天已经补过一次鹿了，明天再来吧！", img);
        }

        // 3. 尝试补签
        LocalDate targetDate = LocalDate.of(now.getYear(), now.getMonth(), day);
        boolean success = saveOrUpdateRecord(userId, targetDate, true);

        if (success) {
            // 4. 更新补签时间
            config.setLastRetroDate(now);
            userConfigMapper.updateById(config);

            byte[] img = generateCalendarImage(userId, now, avatarUrl);
            return new AttendanceResult(true, "成功消耗一次机会补鹿！", img);
        } else {
            byte[] img = generateCalendarImage(userId, now, avatarUrl);
            return new AttendanceResult(false, "那天已经鹿过了，不需要补捏", img);
        }
    }

    /**
     * 查看本月日历
     */
    @Override
    public byte[] viewCalendar(Long userId, String avatarUrl) {
        return generateCalendarImage(userId, LocalDate.now(), avatarUrl);
    }

    /**
     * 查看上月日历
     */
    @Override
    public byte[] viewLastMonthCalendar(Long userId, String avatarUrl) {
        LocalDate lastMonth = LocalDate.now().minusMonths(1);
        return generateCalendarImage(userId, lastMonth, avatarUrl);
    }

    /**
     * 设置是否允许被帮鹿
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean setAllowHelpStatus(Long userId, boolean allow) {
        DeerUserConfig config = getOrCreateUserConfig(userId);
        config.setAllowHelp(allow);
        return userConfigMapper.updateById(config) > 0;
    }

    /**
     * 检查是否允许被帮鹿
     */
    @Override
    public boolean isHelpAllowed(Long userId) {
        DeerUserConfig config = userConfigMapper.selectById(userId);
        return config == null || config.getAllowHelp();
    }

    // ================= 私有辅助方法 =================

    private DeerUserConfig getOrCreateUserConfig(Long userId) {
        DeerUserConfig config = userConfigMapper.selectById(userId);
        if (config == null) {
            config = DeerUserConfig.builder().userId(userId).allowHelp(true).build();
            userConfigMapper.insert(config);
        }
        return config;
    }

    private Map<Long, Boolean> getUserAllowHelpMap(List<Long> userIds) {
        if (userIds.isEmpty()) return Map.of();
        LambdaQueryWrapper<DeerUserConfig> query = new LambdaQueryWrapper<>();
        query.in(DeerUserConfig::getUserId, userIds);
        List<DeerUserConfig> configs = userConfigMapper.selectList(query);
        return configs.stream().collect(Collectors.toMap(DeerUserConfig::getUserId, DeerUserConfig::getAllowHelp));
    }

    private int saveOrUpdateRecordAndGetCount(Long userId, LocalDate date) {
        LambdaQueryWrapper<DeerRecord> query = new LambdaQueryWrapper<>();
        query.eq(DeerRecord::getUserId, userId)
                .eq(DeerRecord::getYear, date.getYear())
                .eq(DeerRecord::getMonth, date.getMonthValue())
                .eq(DeerRecord::getDay, date.getDayOfMonth());

        DeerRecord record = this.getOne(query);

        if (record != null) {
            record.setCount(record.getCount() + 1);
            this.updateById(record);
            return record.getCount();
        } else {
            DeerRecord newRecord = DeerRecord.builder()
                    .userId(userId)
                    .year(date.getYear())
                    .month(date.getMonthValue())
                    .day(date.getDayOfMonth())
                    .count(1)
                    .build();
            this.save(newRecord);
            return 1;
        }
    }

    // 补签专用的 save，如果是 past 且 record 存在则返回 false
    private boolean saveOrUpdateRecord(Long userId, LocalDate date, boolean isPast) {
        LambdaQueryWrapper<DeerRecord> query = new LambdaQueryWrapper<>();
        query.eq(DeerRecord::getUserId, userId)
                .eq(DeerRecord::getYear, date.getYear())
                .eq(DeerRecord::getMonth, date.getMonthValue())
                .eq(DeerRecord::getDay, date.getDayOfMonth());

        DeerRecord record = this.getOne(query);

        if (record != null) {
            // 如果是补签模式，且记录已存在，说明已经签过了，不能再补
            if (isPast) return false;
            record.setCount(record.getCount() + 1);
            return this.updateById(record);
        } else {
            DeerRecord newRecord = DeerRecord.builder()
                    .userId(userId)
                    .year(date.getYear())
                    .month(date.getMonthValue())
                    .day(date.getDayOfMonth())
                    .count(1)
                    .build();
            return this.save(newRecord);
        }
    }

    private byte[] generateBatchReportImage(List<AttendResult> results) {
        Context context = new Context();
        // 过滤掉被拒绝的 (count == -1)
        List<AttendResult> validResults = results.stream().filter(r -> r.count() != -1).toList();
        long failCount = results.size() - validResults.size();

        context.setVariable("results", validResults);
        context.setVariable("totalCount", results.size());
        context.setVariable("successCount", validResults.size());
        context.setVariable("failCount", failCount); // 模板里可以用这个显示有多少人拒绝了

        try {
            String cssContent = new String(DynamicResourceLoader.getInputStream(BATCH_REPORT_CSS_PATH).readAllBytes(), StandardCharsets.UTF_8);
            context.setVariable("cssStyle", cssContent);
        } catch (IOException e) {
            log.error("Failed to load CSS for batch report", e);
        }

        String html = templateEngine.process(TEMPLATE_BATCH_REPORT, context);
        try (Page page = browser.newPage(new Browser.NewPageOptions().setDeviceScaleFactor(playwrightConfig.getDeviceScaleFactor()))) {
            page.setContent(html);
            Locator container = page.locator(".batch-container");
            container.waitFor();
            return container.screenshot(new Locator.ScreenshotOptions().setType(ScreenshotType.PNG));
        }
    }

    private byte[] generateCalendarImage(Long userId, LocalDate dateToRender, String avatarUrl) {
        Context context = new Context();

        // 1. MP 获取指定年月的数据
        LambdaQueryWrapper<DeerRecord> query = new LambdaQueryWrapper<>();
        query.eq(DeerRecord::getUserId, userId)
                .eq(DeerRecord::getYear, dateToRender.getYear())
                .eq(DeerRecord::getMonth, dateToRender.getMonthValue());

        List<DeerRecord> records = this.list(query);

        int lastCount = 0;
        if (dateToRender.getMonth() == LocalDate.now().getMonth()) {
            int todayDay = LocalDate.now().getDayOfMonth();
            lastCount = records.stream()
                    .filter(r -> r.getDay() == todayDay)
                    .findFirst()
                    .map(DeerRecord::getCount)
                    .orElse(0);
        }

        Map<Integer, Integer> deerMap = records.stream()
                .collect(Collectors.toMap(DeerRecord::getDay, DeerRecord::getCount));

        // 2. 构建日历结构
        YearMonth ym = YearMonth.from(dateToRender);
        List<List<DayInfo>> calendar = new ArrayList<>();
        List<DayInfo> currentWeek = new ArrayList<>();

        int firstDayOfWeek = ym.atDay(1).getDayOfWeek().getValue();
        for (int i = 1; i < firstDayOfWeek; i++) {
            currentWeek.add(new DayInfo(0, 0));
        }

        for (int day = 1; day <= ym.lengthOfMonth(); day++) {
            currentWeek.add(new DayInfo(day, deerMap.getOrDefault(day, 0)));
            if (currentWeek.size() == 7) {
                calendar.add(currentWeek);
                currentWeek = new ArrayList<>();
            }
        }
        if (!currentWeek.isEmpty()) {
            while (currentWeek.size() < 7) {
                currentWeek.add(new DayInfo(0, 0));
            }
            calendar.add(currentWeek);
        }

        // 3. 填充 Context
        context.setVariable("year", dateToRender.getYear());
        context.setVariable("month", dateToRender.getMonthValue());
        context.setVariable("calendar", calendar);

        try {
            String avatarBase64 = downloadUrlToBase64(avatarUrl);
            context.setVariable("avatarBase64", avatarBase64);
            String cssContent = new String(DynamicResourceLoader.getInputStream(CALENDAR_CSS_PATH).readAllBytes(), StandardCharsets.UTF_8);
            context.setVariable("cssStyle", cssContent);
            context.setVariable("assets", new Assets(
                    DynamicResourceLoader.getResourceAsBase64(IMG_CHECK_PATH),
                    DynamicResourceLoader.getResourceAsBase64(IMG_PIPE_PATH),
                    DynamicResourceLoader.getResourceAsBase64(getImagePathByCount(lastCount))
            ));
        } catch (IOException e) {
            log.error("Resource loading failed", e);
            throw new RuntimeException("Failed to load resources for calendar generation", e);
        }

        String html = templateEngine.process(TEMPLATE_CALENDAR, context);
        try (Page page = browser.newPage(new Browser.NewPageOptions().setDeviceScaleFactor(playwrightConfig.getDeviceScaleFactor()))) {
            page.setContent(html);
            Locator container = page.locator(".container");
            container.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.ATTACHED));
            return container.screenshot(new Locator.ScreenshotOptions().setType(ScreenshotType.PNG));
        }
    }

    private String downloadUrlToBase64(String url) {
        if (url == null) return null;
        Request request = new Request.Builder().url(url).build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                return Base64.getEncoder().encodeToString(response.body().bytes());
            }
        } catch (Exception e) {
            log.warn("Failed to download avatar: {}", url);
        }
        return null;
    }

    private String getImagePathByCount(int count) {
        if (count >= 50) {
            return CHARCTER_PICTURE_PATHS.get(RandomUtil.randomInt(8,10,true,true));
        } else if (count >= 20) {
            return CHARCTER_PICTURE_PATHS.get(RandomUtil.randomInt(4,7,true,true));
        } else {
            return CHARCTER_PICTURE_PATHS.get(RandomUtil.randomInt(0,3,true,true));
        }
    }
}