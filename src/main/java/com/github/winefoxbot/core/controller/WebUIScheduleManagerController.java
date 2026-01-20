package com.github.winefoxbot.core.controller;

import com.github.winefoxbot.core.init.BotTaskRegistry;
import com.github.winefoxbot.core.model.entity.ShiroScheduleTask;
import com.github.winefoxbot.core.model.enums.PushTargetType;
import com.github.winefoxbot.core.model.vo.webui.req.schedule.TaskIdentifier;
import com.github.winefoxbot.core.model.vo.webui.req.schedule.TaskSaveRequest;
import com.github.winefoxbot.core.model.vo.webui.req.schedule.TaskStatusUpdate;
import com.github.winefoxbot.core.model.vo.webui.resp.TaskTypeResponse;
import com.github.winefoxbot.core.service.schedule.ShiroScheduleTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 调度任务控制器
 * @author FlanChan (badapple495@outlook.com)
 * @since 2026-01-20-13:14
 */
@RestController
@RequestMapping("/api/schedule")
@RequiredArgsConstructor
public class WebUIScheduleManagerController {
    private final ShiroScheduleTaskService scheduleTaskService;
    private final BotTaskRegistry botTaskRegistry;

    /**
     * 获取任务列表
     * 对应 UI 截图中的表格数据加载
     */
    @GetMapping("/list")
    public List<ShiroScheduleTask> list(
            @RequestParam Long botId,
            @RequestParam(required = false) PushTargetType targetType
    ) {
       return scheduleTaskService.listTaskConfigs(botId, targetType);
    }

    /**
     * 获取单个任务详情
     * 用于点击“编辑”按钮时的回显
     */
    @GetMapping("/detail")
    public ShiroScheduleTask getDetail(TaskIdentifier req) {
        return scheduleTaskService.getTaskConfig(
                req.botId(),
                req.targetType(),
                req.targetId(),
                req.taskType()
        );
    }

    /**
     * 立即触发一次任务
     * 对应 UI 中的 "播放/三角形" 按钮
     */
    @PostMapping("/trigger")
    public void triggerTask(@RequestBody TaskIdentifier req) {
        scheduleTaskService.triggerTaskNow(
                req.botId(),
                req.targetType(),
                req.targetId(),
                req.taskType()
        );
    }

    /**
     * 切换任务开启/关闭状态
     * 对应 UI 中的 "Switch 开关"
     */
    @PutMapping("/status")
    public void updateStatus(@RequestBody TaskStatusUpdate req) {
        scheduleTaskService.updateTaskStatus(
                req.botId(),
                req.targetType(),
                req.targetId(),
                req.taskType(),
                req.enable()
        );
    }

    /**
     * 保存或更新任务 (Upsert)
     * 对应 UI 中的 "编辑" 保存或 "新增"
     * 使用 scheduleHandler 方法重新调度
     */
    @PostMapping("/save")
    public void saveOrUpdate(@RequestBody TaskSaveRequest req) {
        scheduleTaskService.scheduleHandler(
                req.botId(),
                req.targetType(),
                req.targetId(),
                req.cronExpression(),
                req.taskType(),
                req.parameter()
        );
    }

    /**
     * 删除/取消任务
     * 对应 UI 中的 "垃圾桶" 按钮
     */
    @DeleteMapping("/remove")
    public void removeTask(@RequestBody TaskIdentifier req) {
        // 取消 JobRunr 调度并处理数据库状态
        scheduleTaskService.cancelTask(
                req.botId(),
                req.targetType(),
                req.targetId(),
                req.taskType()
        );
    }


    /**
     * 获取所有可用的任务类型
     * 用于前端新建任务时的“任务类型”下拉选择框
     */
    @GetMapping("/types")
    public List<TaskTypeResponse> getTaskTypes() {
        // 直接从 Registry 获取缓存好的列表
        return botTaskRegistry.getAvailableTasks();
    }
}