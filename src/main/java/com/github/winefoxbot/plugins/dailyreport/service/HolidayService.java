package com.github.winefoxbot.plugins.dailyreport.service;


import com.github.winefoxbot.plugins.dailyreport.model.dto.HolidayDTO;

import java.util.List;
import java.util.Locale;

/**
 * 节假日计算服务的通用接口.
 * 定义了所有节假日服务实现类必须遵守的契约.
 */
public interface HolidayService {

    /**
     * 获取即将到来的节假日列表，并按时间排序.
     * @return Holiday record 的列表
     */
    List<HolidayDTO> getHolidaysSorted();

    /**
     * 判断当前服务是否支持指定的地区/国家.
     * 这使得我们可以根据需要动态选择实现类.
     * @param locale 地区信息 (例如, Locale.CHINA, Locale.US)
     * @return 如果支持该地区，返回 true, 否则返回 false
     */
    boolean supports(Locale locale);
}
