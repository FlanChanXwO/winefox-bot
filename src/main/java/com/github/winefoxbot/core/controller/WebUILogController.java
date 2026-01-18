package com.github.winefoxbot.core.controller; // 放在你的 controller 包里

import com.github.winefoxbot.core.model.vo.common.Result;
import com.github.winefoxbot.core.service.logging.WebSocketLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author FlanChan
 */
@Controller
@RequiredArgsConstructor
public class WebUILogController {

    private final WebSocketLogService logService;

    private static final String LOG_ROOT = "logs";

    /**
     * 前端订阅 "/app/logs/history" 时触发。
     * 
     * @SubscribeMapping 的特点：
     * 1. 方法的返回值会直接发送给【当前的订阅者】（一对一）。
     * 2. 不会经过 Broker 广播给其他人。
     * 3. 发送完这一次，连接就结束了（对于这个数据的交互而言）。
     */
    @SubscribeMapping("/logs/history") 
    public Collection<String> getLogHistory() {
        return logService.getHistory();
    }


    @ResponseBody
    @GetMapping("/api/logs/available-dates")
    public Result<List<String>> getAvailableDates(@RequestParam("type") String type) {
        try {
            // 1. 确定目录
            String subDir = "history".equals(type) ? "history" : "error";
            Path dirPath = Paths.get(System.getProperty("user.dir"), "logs", subDir);

            if (!Files.exists(dirPath)) {
                return Result.success(Collections.emptyList());
            }

            Set<String> dates = new HashSet<>();
            String todayStr = LocalDate.now().toString();

            // 2. 扫描文件
            try (Stream<Path> stream = Files.list(dirPath)) {
                stream.forEach(path -> {
                    String filename = path.getFileName().toString();

                    // 情况 A: 今天的日志 (app.log 或 error.log)
                    if (filename.equals("app.log") || filename.equals("error.log")) {
                        dates.add(todayStr);
                    }

                    // 情况 B: 历史归档日志 (app-2026-01-15.log)
                    // 使用简单的正则提取日期部分
                    // 假设文件名格式是 xxx-yyyy-MM-dd.log
                    if (filename.matches(".*-\\d{4}-\\d{2}-\\d{2}\\.log")) {
                        // 提取日期部分: 截取倒数第14位到倒数第4位 (yyyy-MM-dd.log 是14个字符)
                        // 或者更简单：用正则提取
                        String datePart = filename.replaceAll(".*(\\d{4}-\\d{2}-\\d{2})\\.log", "$1");
                        dates.add(datePart);
                    }
                });
            }

            // 3. 排序 (最新的在前)
            List<String> sortedDates = dates.stream()
                    .sorted(Comparator.reverseOrder())
                    .collect(Collectors.toList());

            return Result.success(sortedDates);

        } catch (IOException e) {
            e.printStackTrace();
            return Result.error("无法扫描日志目录: " + e.getMessage());
        }
    }


    @ResponseBody
    @GetMapping("/api/logs/content")
    public Result<List<String>> getLogs(
            @RequestParam("date") String dateStr,
            @RequestParam("type") String type // 'history' or 'error'
    ) {
        try {
            // 1. 获取今天的日期字符串 (yyyy-MM-dd)
            String todayStr = LocalDate.now().toString();

            // 2. 确定子目录和文件前缀
            String subDir = "history".equals(type) ? "history" : "error";
            String fileNamePrefix = "history".equals(type) ? "app" : "error";

            String fileName;

            // 3. 关键判断：如果是查询“今天”，文件名就不带日期后缀！
            if (todayStr.equals(dateStr)) {
                fileName = fileNamePrefix + ".log"; // 例如: app.log
            } else {
                fileName = fileNamePrefix + "-" + dateStr + ".log"; // 例如: app-2026-01-17.log
            }

            // 4. 拼接绝对路径 (建议使用 System.getProperty("user.dir") 确保基准路径正确)
            // 你的项目根目录是 winefox-bot，logs 在根目录下
            Path path = Paths.get(System.getProperty("user.dir"), "logs", subDir, fileName);

            if (!Files.exists(path)) {
                // 如果文件不存在，返回空列表
                return Result.success(Collections.emptyList());
            }

            try (Stream<String> lines = Files.lines(path, StandardCharsets.UTF_8)) {
                // 限制一下行数，防止文件太大把内存撑爆 (比如只取最后 2000 行)
                // 或者直接返回全部，取决于你文件大小
                List<String> logList = lines.collect(Collectors.toList());
                return Result.success(logList);
            }

        } catch (IOException e) {
            e.printStackTrace();
            return Result.error("读取失败: " + e.getMessage());
        }
    }
}
