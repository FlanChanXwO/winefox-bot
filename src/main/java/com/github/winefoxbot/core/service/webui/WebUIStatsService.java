package com.github.winefoxbot.core.service.webui;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.github.winefoxbot.core.constants.CacheConstants;
import com.github.winefoxbot.core.model.entity.ShiroGroup;
import com.github.winefoxbot.core.model.entity.ShiroMessage;
import com.github.winefoxbot.core.model.entity.WinefoxBotPluginInvokeStats;
import com.github.winefoxbot.core.model.vo.webui.resp.ConsoleStatsResponse;
import com.github.winefoxbot.core.model.vo.webui.resp.GroupStatsResponse;
import com.github.winefoxbot.core.service.plugin.WinefoxBotPluginInvokeStatsService;
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
     * 获取活跃群组排行 (Top 5)
     * 单独接口，独立缓存
     */
    @Cacheable(value = CacheConstants.WEBUI_ACTIVE_GROUPS_CACHE, key = "'top5'")
    public GroupStatsResponse getActiveGroupStats() {
        log.info("Active groups cache miss. Calculating...");

        // 1. 聚合查询：统计每个群的消息数量，取前5名
        // SQL: SELECT group_id, count(*) as cnt FROM shiro_messages WHERE group_id IS NOT NULL GROUP BY group_id ORDER BY cnt DESC LIMIT 5
        QueryWrapper<ShiroMessage> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("group_id", "count() as count")
                .eq("message_type", "group") // 使用 message_type 判断群聊
                .groupBy("group_id")
                .orderByDesc("count")
                .last("LIMIT 5");

        List<Map<String, Object>> topGroupsMap = shiroMessagesService.listMaps(queryWrapper);

        if (topGroupsMap.isEmpty()) {
            return new GroupStatsResponse(Collections.emptyList());
        }

        // 2. 提取 Group ID 列表
        Set<Long> groupIds = topGroupsMap.stream()
                .map(map -> (Long) map.get("group_id"))
                .collect(Collectors.toSet());

        // 3. 批量查询群组信息 (为了获取群名)
        Map<Long, ShiroGroup> groupInfoMap = shiroGroupsService.list(
                new LambdaQueryWrapper<ShiroGroup>().in(ShiroGroup::getGroupId, groupIds)
        ).stream().collect(Collectors.toMap(ShiroGroup::getGroupId, Function.identity()));

        // 4. 组装结果
        List<GroupStatsResponse.GroupActivity> activityList = topGroupsMap.stream().map(map -> {
            Long groupId = (Long) map.get("group_id");
            // 注意：MyBatis Plus count(*) 返回类型可能是 Long 或 Integer，安全转换
            Long count = Long.valueOf(String.valueOf(map.get("count")));

            ShiroGroup group = groupInfoMap.get(groupId);
            String groupName = (group != null && group.getGroupName() != null)
                    ? group.getGroupName()
                    : "未知群组(" + groupId + ")";

            return new GroupStatsResponse.GroupActivity(groupId, groupName, count);
        }).toList();

        return new GroupStatsResponse(activityList);
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

}
