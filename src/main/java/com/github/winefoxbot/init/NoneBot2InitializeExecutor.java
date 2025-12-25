package com.github.winefoxbot.init;

import com.github.winefoxbot.config.NoneBot2Config;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;

import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-14-21:29
 */
@RequiredArgsConstructor
public class NoneBot2InitializeExecutor {
    private final NoneBot2Config noneBot2Config;

    @Async
    public void execute() throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder();
        // 先设置控制台编码为 UTF-8
        processBuilder.command("chcp", "65001");
        processBuilder.command(noneBot2Config.getCmd().split(" "));
        processBuilder.directory(new File(noneBot2Config.getBotPath()));
        // 设置 PYTHONIOENCODING 环境变量为 utf-8，确保 Python 使用 UTF-8 输出
        processBuilder.environment().put("PYTHONIOENCODING", "utf-8");
        processBuilder.redirectErrorStream(true); // 合并标准输出和错误输出流
        processBuilder.inheritIO();
        Process process = processBuilder.start();

        // 添加 JVM 关闭钩子以确保进程停止时终止 Python 应用
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (process.isAlive()) {
                process.destroy(); // 尝试优雅地终止进程
                try {
                    // 等待进程退出，最多等待 5 秒
                    if (!process.waitFor(5, TimeUnit.SECONDS)) {
                        process.destroyForcibly(); // 强制终止进程
                    }
                } catch (InterruptedException e) {
                    // 如果等待过程中被中断，强制终止进程
                    process.destroyForcibly();
                }
            }
        }));

        // 使用 waitFor() 时，设置最大等待时间来防止长期阻塞
        boolean exited = process.waitFor(60, TimeUnit.SECONDS);
        if (!exited) {
            // 如果 Python 进程在 60 秒内没有退出，强制销毁
            process.destroyForcibly();
            throw new RuntimeException("NoneBot2 process did not exit within the timeout.");
        }

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            throw new RuntimeException("NoneBot2 exited with code: " + exitCode);
        }
    }
}