package com.github.winefoxbot.core.service.webui;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.github.winefoxbot.core.constants.CacheConstants;
import com.github.winefoxbot.core.model.entity.ShiroGroup;
import com.github.winefoxbot.core.model.entity.ShiroMessage;
import com.github.winefoxbot.core.model.entity.WinefoxBotPluginInvokeStats;
import com.github.winefoxbot.core.model.entity.WinefoxBotPluginMeta;
import com.github.winefoxbot.core.model.enums.webui.TimeRange;
import com.github.winefoxbot.core.model.vo.webui.resp.ConsoleStatsResponse;
import com.github.winefoxbot.core.model.vo.webui.resp.InvokeSummaryResponse;
import com.github.winefoxbot.core.model.vo.webui.resp.StatsRankingResponse;
import com.github.winefoxbot.core.service.plugin.WinefoxBotPluginInvokeStatsService;
import com.github.winefoxbot.core.service.plugin.WinefoxBotPluginMetaService;
import com.github.winefoxbot.core.service.shiro.ShiroGroupsService;
import com.github.winefoxbot.core.service.shiro.ShiroMessagesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author FlanChan
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebUIStatsService {

    private final WinefoxBotPluginInvokeStatsService statsService;
    private final WinefoxBotPluginMetaService metaService;

    private final ShiroMessagesService shiroMessagesService;
    private final ShiroGroupsService shiroGroupsService;


    /**
     * 获取仪表盘统计数据
     */
    @Cacheable(value = CacheConstants.WEBUI_CONSOLE_STATS_CACHE, key = "'trend'")
    public ConsoleStatsResponse getConsoleStats() {
        var trend = calculateTrend();
        return new ConsoleStatsResponse(trend);
    }

    /**
     * 获取活跃群组排行 (Top 5) - 支持时间筛选
     * key 生成策略: 'top5_' + 参数 rangeStr
     */
    public List<StatsRankingResponse> getActiveGroupStats(String rangeStr) {
        log.info("Calculating active groups for range: {}", rangeStr);

        // 1. 解析时间范围
        TimeRange range;
        try {
            range = TimeRange.valueOf(rangeStr.toUpperCase());
        } catch (Exception e) {
            range = TimeRange.WEEK;
        }

        LocalDate startDate = calculateStartDate(range);

        // 2. 构造聚合查询
        QueryWrapper<ShiroMessage> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("session_id", "count(*) as count")
                .eq("message_type", "group");

        // 3. 应用时间过滤
        if (startDate != null) {
            // 假设数据库中 time 字段是 datetime 类型，startDate 是 LocalDate
            if (range == TimeRange.DAY) {
                // 如果是"日"，限定为当天的 00:00:00 到次日 00:00:00
                queryWrapper.ge("time", startDate.atStartOfDay())
                        .lt("time", startDate.plusDays(1).atStartOfDay());
            } else {
                // 其他情况 (周/月/年)
                queryWrapper.ge("time", startDate.atStartOfDay());
            }
        }

        queryWrapper.groupBy("session_id")
                .orderByDesc("count")
                .last("LIMIT 5");

        List<Map<String, Object>> topGroupsMap = shiroMessagesService.listMaps(queryWrapper);

        if (topGroupsMap.isEmpty()) {
            return Collections.emptyList();
        }

        // 4. 提取 Group ID 列表 (session_id 在群聊中即为 group_id)
        Set<Long> groupIds = topGroupsMap.stream()
                .map(map -> Long.valueOf(String.valueOf(map.get("session_id"))))
                .collect(Collectors.toSet());

        // 5. 批量查询群组详情 (获取群名)
        Map<Long, ShiroGroup> groupInfoMap = shiroGroupsService.list(
                new LambdaQueryWrapper<ShiroGroup>().in(ShiroGroup::getGroupId, groupIds)
        ).stream().collect(Collectors.toMap(ShiroGroup::getGroupId, Function.identity()));

        // 6. 转换为 StatsRankingResponse 列表
        return topGroupsMap.stream().map(map -> {
            Long groupId = Long.valueOf(String.valueOf(map.get("session_id")));
            // 安全转换 count 数值类型
            Number countNum = (Number) map.get("count");
            long count = countNum == null ? 0L : countNum.longValue();

            ShiroGroup group = groupInfoMap.get(groupId);
            String groupName = (group != null && group.getGroupName() != null)
                    ? group.getGroupName()
                    : String.valueOf(groupId); // 若无群名则显示群号

            // 返回通用统计响应对象 (id, name, value)
            return new StatsRankingResponse(String.valueOf(groupId), groupName, count);
        }).toList();
    }



    /**
     * 2. 计算趋势图 (最近30天)
     */
    private ConsoleStatsResponse.TrendChartData calculateTrend() {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(29);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd");

        // 查出该时间段内的所有数据
        var invokeStatsList = statsService.list(new LambdaQueryWrapper<WinefoxBotPluginInvokeStats>()
                .ge(WinefoxBotPluginInvokeStats::getStatDate, startDate));

        // 查出该时间段内的所有数据
        var messageStatsList = shiroMessagesService.list(new LambdaQueryWrapper<ShiroMessage>()
                .ge(ShiroMessage::getTime, startDate));

        // 按日期分组求和
        Map<LocalDate, Long> callMap = invokeStatsList.stream()
                .collect(Collectors.groupingBy(
                        WinefoxBotPluginInvokeStats::getStatDate,
                        Collectors.summingLong(WinefoxBotPluginInvokeStats::getCallCount)
                ));

        Map<LocalDate, Long> msgMap = messageStatsList.stream()
                .collect(Collectors.groupingBy(
                        msg -> msg.getTime().toLocalDate(),
                        Collectors.summingLong(_ -> 1L)
                ));

        List<String> dates = new ArrayList<>();
        List<Long> callCounts = new ArrayList<>();
        List<Long> msgCounts = new ArrayList<>();

        // 填充数据，保证日期连续
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            dates.add(date.format(formatter));
            
            long calls = callMap.getOrDefault(date, 0L);
            callCounts.add(calls);
            long msgs = msgMap.getOrDefault(date, 0L);
            msgCounts.add(msgs);
        }

        return new ConsoleStatsResponse.TrendChartData(dates, msgCounts, callCounts);
    }


    /**
     * 获取调用统计概览 (总数/日/周/月/年)
     */
    public InvokeSummaryResponse getInvokeSummary() {
        LocalDate today = LocalDate.now();

        // 为了性能，这里使用 5 次轻量级聚合查询
        // 也可以写自定义 SQL 一次查出，但 MP 原生写法如下更易维护

        long total = getSumCount(null); // 所有时间
        long day = getSumCount(today); // 今天
        long week = getSumCount(today.minusWeeks(1)); // 一周内 (过去7天)
        long month = getSumCount(today.minusMonths(1)); // 一月内
        long year = getSumCount(today.minusYears(1)); // 一年内

        return new InvokeSummaryResponse(total, day, week, month, year);
    }

    /**
     * 获取插件调用排行 (支持时间筛选)
     */
    public List<StatsRankingResponse> getPluginRanking(String rangeStr) {
        TimeRange range;
        try {
            range = TimeRange.valueOf(rangeStr.toUpperCase());
        } catch (Exception e) {
            range = TimeRange.WEEK; // 默认按周
        }

        LocalDate startDate = calculateStartDate(range);

        // 使用 MyBatis Plus 的 QueryWrapper 进行分组聚合查询
        // SELECT plugin_class_name, SUM(call_count) as total FROM stats WHERE stat_date >= ? GROUP BY plugin_class_name ORDER BY total DESC LIMIT 5
        QueryWrapper<WinefoxBotPluginInvokeStats> wrapper = new QueryWrapper<>();
        wrapper.select("plugin_class_name", "SUM(call_count) as total_count");

        if (startDate != null) {
            if (range == TimeRange.DAY) {
                wrapper.eq("stat_date", startDate);
            } else {
                wrapper.ge("stat_date", startDate);
            }
        }

        wrapper.groupBy("plugin_class_name")
                .orderByDesc("total_count") // 按总数降序
                .last("LIMIT 5");           // 只取前5

        List<Map<String, Object>> resultMaps = statsService.listMaps(wrapper);

        // 获取插件名称映射
        Map<String, String> nameMap = metaService.list().stream()
                .collect(Collectors.toMap(WinefoxBotPluginMeta::getClassName, WinefoxBotPluginMeta::getDisplayName, (v1, v2) -> v1));

        return resultMaps.stream().map(map -> {
            String className = (String) map.get("plugin_class_name");
            // 注意：不同数据库 SUM 返回类型可能不同 (BigDecimal, Long, Double)，这里做安全转换
            Number totalNum = (Number) map.get("total_count");
            long total = totalNum == null ? 0L : totalNum.longValue();

            String displayName = nameMap.getOrDefault(className, className);
            // 简单处理类名显示，比如 com.xxx.SetuPlugin -> SetuPlugin (如果没有显示名称)
            if (displayName.equals(className)) {
                displayName = className.substring(className.lastIndexOf('.') + 1);
            }

            return new StatsRankingResponse(className, displayName, total);
        }).toList();
    }

    private LocalDate calculateStartDate(TimeRange range) {
        LocalDate today = LocalDate.now();
        return switch (range) {
            case DAY -> today;
            case WEEK -> today.minusWeeks(1); // 或者用 TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)
            case MONTH -> today.minusMonths(1);
            case YEAR -> today.minusYears(1);
            case ALL -> null; // 全部时间不需要起始日期
        };
    }

    // --- Helper ---

    private long getSumCount(LocalDate startDate) {
        var wrapper = new LambdaQueryWrapper<WinefoxBotPluginInvokeStats>();
        if (startDate != null) {
            // 如果是具体某一天 (stats表中存的是 LocalDate)
            // 逻辑调整：如果是"一天内"，通常指 statDate == today
            // 如果是"一周内"，通常指 statDate >= today - 7

            if (startDate.isEqual(LocalDate.now())) {
                wrapper.eq(WinefoxBotPluginInvokeStats::getStatDate, startDate);
            } else {
                wrapper.ge(WinefoxBotPluginInvokeStats::getStatDate, startDate);
            }
        }

        // select sum(call_count)
        List<Object> objs = statsService.getBaseMapper().selectObjs(
                wrapper.select(WinefoxBotPluginInvokeStats::getCallCount)
        );

        return objs.stream()
                .filter(Objects::nonNull)
                .mapToLong(o -> ((Number) o).longValue())
                .sum();
    }


}
