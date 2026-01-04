package com.github.winefoxbot.core.utils;

import java.io.InputStream;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-10-20:29
 */
public final class CommandUtil {
    private CommandUtil () {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static void runCmd(String... cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        p.waitFor();
    }

    public static String runCmdGetOutput(String... cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        InputStream is = p.getInputStream();
        return new String(is.readAllBytes());
    }

    public static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

}