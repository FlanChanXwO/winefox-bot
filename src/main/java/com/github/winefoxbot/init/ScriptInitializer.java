package com.github.winefoxbot.init;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.system.ApplicationHome;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.FileCopyUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

@Component
@Slf4j
public class ScriptInitializer implements ApplicationRunner {

    private static final String SCRIPT_DIR = "scripts/";
    private static final String[] SCRIPT_NAMES = {"start.bat", "start.sh"};
    private boolean scriptsGenerated = false;

    @Override
    public void run(ApplicationArguments args) {
        try {
            // 使用 ApplicationHome 来可靠地确定应用的源（jar 文件或目录）
            ApplicationHome applicationHome = new ApplicationHome(ScriptInitializer.class);
            File source = applicationHome.getSource(); // 这会得到启动的 jar 文件

            // 如果 source 是 null 或者它不是一个文件（比如在 IDE 中运行时它可能是一个目录），则跳过
            if (source == null || !source.isFile()) {
                log.info("应用在非jar包环境中运行 (例如 IDE)，跳过启动脚本生成。");
                return;
            }

            // 获取 jar 文件所在的目录
            File jarDir = applicationHome.getDir();

            if (jarDir == null || !jarDir.isDirectory()) {
                log.warn("无法确定应用的运行目录，跳过启动脚本生成。");
                return;
            }

            // 遍历需要释放的脚本
            for (String scriptName : SCRIPT_NAMES) {
                releaseScript(jarDir, scriptName);
            }

            // 如果有新脚本生成，就打印提示信息
            if (scriptsGenerated) {
                printUsageInstructions(jarDir);
            }

        } catch (Exception e) {
            log.error("初始化启动脚本时发生错误", e);
        }
    }

    // releaseScript 和 printUsageInstructions 方法保持不变...
    // ... (请将之前版本中的这两个方法复制到这里)
    /**
     * 从 resources 中释放单个脚本文件到指定目录
     */
    private void releaseScript(File targetDir, String scriptName) throws IOException {
        File scriptFile = new File(targetDir, scriptName);

        // 如果脚本文件已存在，则不进行任何操作
        if (scriptFile.exists()) {
            return;
        }

        log.info("启动脚本 '{}' 不存在，将从资源文件中创建...", scriptName);

        // 从 classpath 读取资源
        ClassPathResource resource = new ClassPathResource(SCRIPT_DIR + scriptName);
        if (!resource.exists()) {
            log.warn("资源文件 '{}' 未找到，无法创建启动脚本。", SCRIPT_DIR + scriptName);
            return;
        }

        // 复制文件
        try (InputStream inputStream = resource.getInputStream();
             FileOutputStream outputStream = new FileOutputStream(scriptFile)) {
            FileCopyUtils.copy(inputStream, outputStream);
        }

        // 如果是 .sh 脚本，在非 Windows 系统上为其添加可执行权限
        if (scriptName.endsWith(".sh") && !System.getProperty("os.name").toLowerCase().startsWith("windows")) {
            if (scriptFile.setExecutable(true)) {
                log.info("已为 '{}' 添加可执行权限。", scriptName);
            } else {
                log.warn("为 '{}' 添加可执行权限失败，请手动执行 'chmod +x {}'", scriptName, scriptName);
            }
        }

        scriptsGenerated = true;
        log.info("成功创建启动脚本: {}", scriptFile.getAbsolutePath());
    }

    /**
     * 打印使用说明
     */
    private void printUsageInstructions(File dir) {
        log.info("==========================================================================");
        log.info("    启动脚本已生成在您的应用目录中: {}", dir.getAbsolutePath());
        log.info("    为了使 '/重启' 功能正常工作，请停止当前应用 (Ctrl+C)，");
        log.info("    并使用以下脚本重新启动：");
        log.info("    - 在 Windows 上, 请双击或运行: start.bat");
        log.info("    - 在 Linux / macOS 上, 请运行: ./start.sh");
        log.info("==========================================================================");
    }
}
