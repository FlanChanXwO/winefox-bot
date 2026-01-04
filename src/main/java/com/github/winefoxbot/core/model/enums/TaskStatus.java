package com.github.winefoxbot.core.model.enums;

public enum TaskStatus {
    PENDING,   // 待执行/调度中
    COMPLETED, // 任务已完成 (对于单次任务是执行成功，对于固定次数任务是达到次数)
    CANCELED,  // 已取消
    FAILED     // 最近一次执行失败 (周期性任务可能会继续尝试)
}
