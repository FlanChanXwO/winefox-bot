
package com.github.winefoxbot.init;

import org.springframework.boot.system.ApplicationHome;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.FileCopyUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * 应用启动前的脚本检查器。
 * 这是一个独立的工具类，不依赖于Spring上下文，可以在main方法中直接调用。
 * 它负责检查并生成管理脚本，并决定是否需要提前退出应用。
 */
public class ScriptChecker {

    private static final String SCRIPT_DIR_IN_RESOURCES = "scripts/";
    private static final String[] SCRIPT_NAMES = {"control.bat", "control.sh", "start-daemon.bat", "start-daemon.sh"};

    // Add this block at the top of the class
    static {
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8));
    }

    /**
     * 执行检查。如果生成了新脚本，则打印说明并退出程序。
     *
     * @param mainClass 通常是你的 @SpringBootApplication 主类
     */
    public static void checkAndDeploy(Class<?> mainClass) {
        try {
            ApplicationHome home = new ApplicationHome(mainClass);
            File source = home.getSource();

            // 如果不是以jar包形式运行 (例如在IDE中)，则直接返回，不执行任何操作。
            if (source == null || !source.isFile()) {
                System.out.println("[ScriptChecker] 检测到在 IDE 环境中运行，跳过脚本部署。");
                return;
            }

            File jarDir = home.getDir();
            boolean scriptsGenerated = false;

            for (String scriptName : SCRIPT_NAMES) {
                if (releaseScript(jarDir, scriptName)) {
                    scriptsGenerated = true;
                }
            }

            if (scriptsGenerated) {
                printUsageAndExit(jarDir);
            }

        } catch (Exception e) {
            System.err.println("[ScriptChecker] 脚本初始化失败: " + e.getMessage());
            e.printStackTrace();
            // 在这种关键错误下，最好也退出
            System.exit(1);
        }
    }

    private static boolean releaseScript(File targetDir, String scriptName) throws IOException {
        File scriptFile = new File(targetDir, scriptName);
        if (scriptFile.exists()) {
            return false; // 文件已存在，未生成新脚本
        }

        System.out.printf("[ScriptChecker] 正在创建管理脚本: %s...%n", scriptName);

        ClassPathResource resource = new ClassPathResource(SCRIPT_DIR_IN_RESOURCES + scriptName);
        if (!resource.exists()) {
            System.err.printf("[ScriptChecker] 警告: 资源文件 '%s' 未在jar包中找到!%n", SCRIPT_DIR_IN_RESOURCES + scriptName);
            return false;
        }

        try (InputStream in = resource.getInputStream(); FileOutputStream out = new FileOutputStream(scriptFile)) {
            FileCopyUtils.copy(in, out);
        }

        // 为 .sh 脚本添加可执行权限
        if (scriptName.endsWith(".sh") && !System.getProperty("os.name").toLowerCase().startsWith("windows")) {
            if (scriptFile.setExecutable(true)) {
                System.out.printf("[ScriptChecker] 已为 '%s' 添加可执行权限。%n", scriptName);
            } else {
                System.err.printf("[ScriptChecker] 警告: 无法为 '%s' 添加可执行权限, 请手动执行 'chmod +x %s'%n", scriptName, scriptName);
            }
        }
        return true; // 成功生成了新脚本
    }

    private static void printUsageAndExit(File dir) {
        String absolutePath = dir.getAbsolutePath();
        // 因为还没有Logger，我们使用 System.out
        System.out.println("======================================================================================");
        System.out.println(">> 首次运行设置完成！专业的应用管理脚本已生成在您的应用目录中。");
        System.out.println(">> 目录: " + absolutePath);
        System.out.println("--------------------------------------------------------------------------------------");
        System.out.println(">> 为了使后台运行、日志管理和自动更新等功能生效，当前进程已停止。");
        System.out.println(">> 请使用以下新脚本来启动和管理您的应用：");
        System.out.println();
        System.out.println("   [ 在 Windows 上 ]");
        System.out.println("   - 启动应用:   control.bat start");
        System.out.println("   - 停止应用:   control.bat stop");
        System.out.println("   - 查看状态:   control.bat status");
        System.out.println("   - 查看日志:   control.bat logs");
        System.out.println("   - 实时日志:   control.bat logs -f  (需要 PowerShell)");
        System.out.println();
        System.out.println("   [ 在 Linux / macOS 上 ]");
        System.out.println("   - (首次)添加权限: chmod +x *.sh");
        System.out.println("   - 启动应用:      ./control.sh start");
        System.out.println("   - 停止应用:      ./control.sh stop");
        System.out.println("   - 查看状态:      ./control.sh status");
        System.out.println("   - 查看日志:      ./control.sh logs");
        System.out.println("   - 实时日志:      ./control.sh logs -f");
        System.out.println("======================================================================================");
        System.exit(0);
    }
}
