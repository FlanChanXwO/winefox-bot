package com.github.winefoxbot.core.service.webui;

import com.github.winefoxbot.core.model.dto.webui.SystemStatusDTO;
import org.springframework.stereotype.Service;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.software.os.OSFileStore;
import oshi.software.os.OperatingSystem;

import java.text.DecimalFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class SystemMonitorService {

    private final SystemInfo systemInfo = new SystemInfo();
    private final HardwareAbstractionLayer hardware = systemInfo.getHardware();
    private final OperatingSystem os = systemInfo.getOperatingSystem();
    private final DecimalFormat df = new DecimalFormat("#.0"); // 保留一位小数

    public SystemStatusDTO getSystemStatus() {

        // 1. 获取 CPU 使用率
        CentralProcessor processor = hardware.getProcessor();
        long[] prevTicks = processor.getSystemCpuLoadTicks();
        // 必须休眠一小段时间才能计算出瞬时负载，或者保存上一次的状态
        // 这里为了简单直接 sleep，但在高并发场景建议使用缓存上一次ticks的方式计算
        try { TimeUnit.MILLISECONDS.sleep(200); } catch (InterruptedException ignored) {}
        double cpuLoad = processor.getSystemCpuLoadBetweenTicks(prevTicks) * 100;

        // 2. 获取内存使用率
        GlobalMemory memory = hardware.getMemory();
        double memoryLoad = 100d * (memory.getTotal() - memory.getAvailable()) / memory.getTotal();

        // 3. 获取磁盘使用率 (通常取根目录或者最大挂载点)
        List<OSFileStore> fileStores = os.getFileSystem().getFileStores();
        long total = 0;
        long used = 0;
        for (OSFileStore fs : fileStores) {
            // 过滤掉虚拟文件系统等，只计算本地磁盘
            if (fs.getTotalSpace() > 1024 * 1024 * 1024) { 
                total += fs.getTotalSpace();
                used += (fs.getTotalSpace() - fs.getUsableSpace());
            }
        }
        double diskLoad = total > 0 ? 100d * used / total : 0;

        return new SystemStatusDTO(df.format(cpuLoad) + "%", df.format(memoryLoad) + "%", df.format(diskLoad) + "%");
    }
}
