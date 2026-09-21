package com.chenru1chao.constant;

public class SandboxLimits {
    public static final long TIME_LIMIT_COMPILE_MS = 5000;
    public static final long COMPILE_MEMORY_LIMIT_MB = 256;
    public static final long OUTPUT_LIMIT_BYTES = 10 * 1024 * 1024;
    // 获取当前JDK的文件地址 C:\Users\陈睿超\.jdks\temurin-17.0.19
    public static final String JDK_HOME = System.getProperty("java.home");
    // 判断当前是否是windows系统 Windows 11
    public static final boolean IS_WINDOWS  = System.getProperty("os.name").toLowerCase().contains("win");

}
