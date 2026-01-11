
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

    // 定义农历节日
    private static final Map<String, LunarDate> LUNAR_FESTIVALS = new LinkedHashMap<>();
    static {
        LUNAR_FESTIVALS.put("春节", new LunarDate(1, 1));
        LUNAR_FESTIVALS.put("端午节", new LunarDate(5, 5));
        LUNAR_FESTIVALS.put("中秋节", new LunarDate(8, 15));
        // Hutool 的 ChineseDate 支持闰月，但除夕通常是腊月最后一天，
        // 计算较为复杂，此处简化为腊月三十，对于小年夜的情况也能基本覆盖。
        LUNAR_FESTIVALS.put("除夕", new LunarDate(12, 30));
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
        int currentYear = today.getYear();
        List<HolidayDTO> holidayList = new ArrayList<>();

        // 1. 处理农历节日
        LUNAR_FESTIVALS.forEach((name, lunarDate) -> {
            LocalDate festivalDate = lunarToSolar(currentYear, lunarDate);
            // 如果计算出的节日日期在今天之前，就计算明年的
            if (festivalDate.isBefore(today)) {
                festivalDate = lunarToSolar(currentYear + 1, lunarDate);
            }
            addHolidayToList(holidayList, name, festivalDate, today);
        });

        // 2. 处理公历节日
        SOLAR_FESTIVALS.forEach((name, dateInfo) -> {
            LocalDate festivalDate = LocalDate.of(currentYear, dateInfo.getKey(), dateInfo.getValue());
            if (festivalDate.isBefore(today)) {
                festivalDate = festivalDate.plusYears(1);
            }
            addHolidayToList(holidayList, name, festivalDate, today);
        });

        // 3. 按剩余天数排序
        holidayList.sort(Comparator.comparingInt(HolidayDTO::daysLeft));
        return holidayList;
    }

    @Override
    public boolean supports(Locale locale) {
        return Locale.CHINA.equals(locale);
    }

    /**
     * 将农历日期转换为公历日期 (使用 Hutool 实现)
     * @param year 公历年份
     * @param lunarDate 农历月和日
     * @return 对应的公历日期 (LocalDate)
     */
    private LocalDate lunarToSolar(int year, LunarDate lunarDate) {
        // 使用 Hutool 的 ChineseDate 进行转换
        // 构造函数 ChineseDate(int chineseYear, int chineseMonth, int chineseDay)
        ChineseDate cd = new ChineseDate(year, lunarDate.month, lunarDate.day);
        
        // 将 Hutool 的 ChineseDate 对象转换为 java.util.Date
        Date gregorianDate = cd.getGregorianDate();

        // 将 java.util.Date 转换为 java.time.LocalDate (推荐)
        return gregorianDate.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
    }

    /**
     * 辅助方法，计算天数并添加到列表
     */
    private void addHolidayToList(List<HolidayDTO> list, String name, LocalDate date, LocalDate today) {
        long daysLeft = ChronoUnit.DAYS.between(today, date);
        list.add(new HolidayDTO((int) daysLeft, name, date));
    }
}
