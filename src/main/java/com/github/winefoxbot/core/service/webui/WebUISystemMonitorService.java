package com.github.winefoxbot.core.service.webui;

import com.github.winefoxbot.core.model.vo.webui.resp.SystemStatusResponse;
import org.springframework.stereotype.Service;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.software.os.OSFileStore;
import oshi.software.os.OperatingSystem;

import java.text.DecimalFormat;
import java.util.List;

@Service
public class WebUISystemMonitorService {

    private final SystemInfo systemInfo = new SystemInfo();
    private final HardwareAbstractionLayer hardware = systemInfo.getHardware();
    private final OperatingSystem os = systemInfo.getOperatingSystem();
    // 使用 JDK 21 的 String Template 需要开启预览，这里为了通用仍使用 DecimalFormat，但做静态常量优化
    private static final DecimalFormat DF = new DecimalFormat("0.0"); // 修改格式：0.0 避免 .0% 的情况

    // 保存上一次的 CPU ticks
    private long[] prevTicks;

    public WebUISystemMonitorService() {
        // 初始化时先获取一次 ticks
        this.prevTicks = hardware.getProcessor().getSystemCpuLoadTicks();
    }

    public SystemStatusResponse getSystemStatus() {
        CentralProcessor processor = hardware.getProcessor();

        // --- 1. 获取 CPU 使用率 (无阻塞算法) ---
        // 获取当前的 ticks
        long[] currentTicks = processor.getSystemCpuLoadTicks();
        // 计算当前 ticks 和上一次 ticks 之间的负载
        double cpuLoad = processor.getSystemCpuLoadBetweenTicks(prevTicks) * 100;

        // 更新 prevTicks 为当前 ticks，供下一次计算使用
        // 注意：这种方式计算的是"两次请求之间"的平均负载，比瞬时 sleep 更能反映真实压力
        prevTicks = currentTicks;

        // 如果计算结果是 NaN (通常发生在刚启动第一次计算时)，默认给 0
        if (Double.isNaN(cpuLoad)) {
            cpuLoad = 0.0;
        }

        // --- 2. 获取内存使用率 (JDK 21 风格简化) ---
        GlobalMemory memory = hardware.getMemory();
        double memoryLoad = 100d * (memory.getTotal() - memory.getAvailable()) / memory.getTotal();

        // --- 3. 获取磁盘使用率 ---
        List<OSFileStore> fileStores = os.getFileSystem().getFileStores();
        long total = 0;
        long used = 0;

        // 使用 Stream API 简化计算 (更适合现代 Java)
        for (OSFileStore fs : fileStores) {
            long totalSpace = fs.getTotalSpace();
            // 简单过滤逻辑：只统计大于 1GB 的盘
            if (totalSpace > 1L * 1024 * 1024 * 1024) {
                total += totalSpace;
                used += (totalSpace - fs.getUsableSpace());
            }
        }
        double diskLoad = total > 0 ? 100d * used / total : 0;

        return new SystemStatusResponse(
                DF.format(cpuLoad) + "%",
                DF.format(memoryLoad) + "%",
                DF.format(diskLoad) + "%"
        );
    }
}
