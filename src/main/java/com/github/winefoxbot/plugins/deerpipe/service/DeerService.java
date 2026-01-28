package com.github.winefoxbot.plugins.deerpipe.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.github.winefoxbot.plugins.deerpipe.model.dto.AttendanceResult;
import com.github.winefoxbot.plugins.deerpipe.model.dto.BatchTarget;
import com.github.winefoxbot.plugins.deerpipe.model.entity.DeerRecord;

import java.util.List;

public interface DeerService extends IService<DeerRecord> {

    byte[] attend(Long userId, String avatarUrl);

    // 帮鹿专用接口，包含权限检查
    byte[] attendByOther(Long targetUserId, String targetNickname, String avatarUrl);

    // 批量帮鹿，包含权限检查
    byte[] batchAttendAndRender(List<BatchTarget> targets);

    AttendanceResult attendPast(Long userId, int day, String avatarUrl);

    byte[] viewCalendar(Long userId, String avatarUrl);

    // 新增：查看上月鹿历
    byte[] viewLastMonthCalendar(Long userId, String avatarUrl);

    // 新增：设置是否允许被帮鹿
    boolean setAllowHelpStatus(Long userId, boolean allow);

    // 新增：获取用户是否允许被帮鹿
    boolean isHelpAllowed(Long userId);
}
