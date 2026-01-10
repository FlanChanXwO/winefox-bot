
package com.github.winefoxbot.core.init;

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

            // --- 新增代码开始: 强制重命名 Jar 包 ---
            String targetJarName = "winefox-bot.jar";
            if (!source.getName().equals(targetJarName)) {
                File targetJar = new File(source.getParent(), targetJarName);

                // 尝试重命名 (Windows下运行中的Jar可能无法重命名，Linux通常可以)
                // 注意：如果在 Windows 上直接运行 java -jar xxx.jar，文件会被锁定，重命名通常会失败。
                // 如果是复制一份再改名是没问题的，但无法改变当前运行的文件名。
                // 这里我们尝试重命名，如果成功则打印提示。
                boolean renameSuccess = source.renameTo(targetJar);

                if (renameSuccess) {
                    System.out.printf("[ScriptChecker] 检测到 Jar 包名称不标准，已自动重命名为: %s%n", targetJarName);
                    // 因为物理文件改名了，为了保证后续脚本逻辑正确，这里可能需要更新 source 引用
                    // 但通常 ApplicationHome 是一次性的，后续脚本生成只依赖目录路径，所以影响不大。
                    // 只是此时若继续运行可能会有问题（因为 classpath 里的 jar 名字变了），建议提示重启。
                    System.out.println("[ScriptChecker] 为了确保系统稳定，请使用新文件名重新启动或直接使用脚本管理。");
                    System.exit(0);
                } else {
                    // 如果重命名失败（常见于 Windows 文件被锁定），尝试复制一份
                    try {
                        System.out.printf("[ScriptChecker] 无法直接重命名（文件可能被锁定），正在尝试复制为标准名称: %s%n", targetJarName);
                        FileCopyUtils.copy(source, targetJar);
                        System.out.println("[ScriptChecker] 复制成功。请后续使用 " + targetJarName + " 或配套脚本启动。");
                        // 既然已经生成了标准包，建议让用户去用那个标准的包
                    } catch (IOException ex) {
                        System.err.println("[ScriptChecker] 重命名/复制 Jar 包失败: " + ex.getMessage());
                    }
                }
            }
            // --- 新增代码结束 ---

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
        System.out.println("   - 实时日志:   control.ps1 logs -f （使用 PowerShell）");
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
