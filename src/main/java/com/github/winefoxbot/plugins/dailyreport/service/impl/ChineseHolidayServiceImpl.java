package com.github.winefoxbot.plugins.dailyreport.service.impl;

import cn.hutool.core.date.ChineseDate;
import com.github.winefoxbot.plugins.dailyreport.model.dto.HolidayDTO;
import com.github.winefoxbot.plugins.dailyreport.service.HolidayService;

import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * @author FlanChan
 */
public class ChineseHolidayServiceImpl implements HolidayService {

    private record LunarDate(int month, int day) {}

    // 定义农历节日 (移除除夕，单独特殊处理)
    private static final Map<String, LunarDate> LUNAR_FESTIVALS = new LinkedHashMap<>();
    static {
        LUNAR_FESTIVALS.put("春节", new LunarDate(1, 1));
        LUNAR_FESTIVALS.put("端午节", new LunarDate(5, 5));
        LUNAR_FESTIVALS.put("中秋节", new LunarDate(8, 15));
    }

    // 定义公历节日
    private static final Map<String, Map.Entry<Month, Integer>> SOLAR_FESTIVALS = new LinkedHashMap<>();
    static {
        SOLAR_FESTIVALS.put("元旦", new AbstractMap.SimpleEntry<>(Month.JANUARY, 1));
        SOLAR_FESTIVALS.put("劳动节", new AbstractMap.SimpleEntry<>(Month.MAY, 1));
        SOLAR_FESTIVALS.put("国庆节", new AbstractMap.SimpleEntry<>(Month.OCTOBER, 1));
    }


    @Override
    public List<HolidayDTO> getHolidaysSorted() {
        LocalDate today = LocalDate.now();
        int currentGregorianYear = today.getYear();

        // 例如：公历2026年1月12日，实际上是农历2025年。如果不转，直接用2026去算春节，会算出2027年的春节。
        ChineseDate chineseToday = new ChineseDate(today);
        int currentLunarYear = chineseToday.getChineseYear();

        List<HolidayDTO> holidayList = new ArrayList<>();

        // 1. 处理普通农历节日
        LUNAR_FESTIVALS.forEach((name, lunarDate) -> {
            LocalDate festivalDate = calculateNextLunarDate(currentLunarYear, lunarDate.month, lunarDate.day, today);
            addHolidayToList(holidayList, name, festivalDate, today);
        });

        // 2. 处理除夕
        // 逻辑：找到下一个"春节"，然后减去1天，即为除夕。这样可以完美解决腊月二十九/三十的问题。
        // 先算今年的春节 (基于当前农历年)
        LocalDate springFestivalThisLunarYear = getSolarDateFromLunar(currentLunarYear, 1, 1);
        // 今年的除夕 = 今年的春节 - 1天
        LocalDate chuXiThisLunarYear = springFestivalThisLunarYear.minusDays(1);

        LocalDate finalChuXi;
        if (chuXiThisLunarYear.isBefore(today)) {
            // 如果今年的除夕已过，就算明年的春节-1天
            LocalDate springFestivalNextLunarYear = getSolarDateFromLunar(currentLunarYear + 1, 1, 1);
            finalChuXi = springFestivalNextLunarYear.minusDays(1);
        } else {
            finalChuXi = chuXiThisLunarYear;
        }
        addHolidayToList(holidayList, "除夕", finalChuXi, today);


        // 3. 处理公历节日
        SOLAR_FESTIVALS.forEach((name, dateInfo) -> {
            LocalDate festivalDate = LocalDate.of(currentGregorianYear, dateInfo.getKey(), dateInfo.getValue());
            if (festivalDate.isBefore(today)) {
                festivalDate = festivalDate.plusYears(1);
            }
            addHolidayToList(holidayList, name, festivalDate, today);
        });

        // 4. 按剩余天数排序
        holidayList.sort(Comparator.comparingInt(HolidayDTO::daysLeft));
        return holidayList;
    }

    @Override
    public boolean supports(Locale locale) {
        return Locale.CHINA.equals(locale);
    }

    /**
     * 计算下一个最近的农历节日公历日期
     */
    private LocalDate calculateNextLunarDate(int currentLunarYear, int month, int day, LocalDate today) {
        // 尝试计算当前农历年的节日
        LocalDate date = getSolarDateFromLunar(currentLunarYear, month, day);

        // 如果当前农历年的节日已经过了（比如今天是农历八月，要算端午五月），则计算下一年
        if (date.isBefore(today)) {
            date = getSolarDateFromLunar(currentLunarYear + 1, month, day);
        }
        return date;
    }


    /**
     * 将农历日期转换为公历日期
     */
    private LocalDate getSolarDateFromLunar(int lunarYear, int lunarMonth, int lunarDay) {
        try {
            ChineseDate cd = new ChineseDate(lunarYear, lunarMonth, lunarDay);
            Date gregorianDate = cd.getGregorianDate();
            return gregorianDate.toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate();
        } catch (Exception e) {
            // 防止闰月或非法日期导致的异常，虽然Hutool通常处理得很好
            // 如果出错，默认返回一个较远的日期避免报错阻断流程
            return LocalDate.now().plusYears(1);
        }
    }

    /**
     * 辅助方法，计算天数并添加到列表
     */
    private void addHolidayToList(List<HolidayDTO> list, String name, LocalDate date, LocalDate today) {
        long daysLeft = ChronoUnit.DAYS.between(today, date);
        list.add(new HolidayDTO((int) daysLeft, name, date));
    }
}
