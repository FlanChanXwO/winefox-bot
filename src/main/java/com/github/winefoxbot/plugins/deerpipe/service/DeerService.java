package com.github.winefoxbot.plugins.deerpipe.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.github.winefoxbot.plugins.deerpipe.model.dto.AttendanceResult;
import com.github.winefoxbot.plugins.deerpipe.model.dto.BatchTarget;
import com.github.winefoxbot.plugins.deerpipe.model.entity.DeerRecord;

import java.util.List;

public interface DeerService extends IService<DeerRecord> {

    byte[] attend(Long userId, String avatarUrl);

    byte[] attendByOther(Long targetUserId, String targetNickname, String avatarUrl);

    byte[] batchAttendAndRender(List<BatchTarget> targets);

    AttendanceResult attendPast(Long userId, int day, String avatarUrl);

    byte[] viewCalendar(Long userId, String avatarUrl);

    byte[] viewLastMonthCalendar(Long userId, String avatarUrl);

    void setAllowHelpStatus(Long userId, boolean allow);

    boolean isHelpAllowed(Long userId);
}
