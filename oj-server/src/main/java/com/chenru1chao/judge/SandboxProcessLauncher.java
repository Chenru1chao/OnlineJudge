package com.chenru1chao.judge;

import com.chenru1chao.exception.SandboxException;
import com.chenru1chao.judge.model.ExecutorResult;
import com.chenru1chao.judge.model.TestCase;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static com.chenru1chao.constant.SandboxLimits.OUTPUT_LIMIT_BYTES;

public class SandboxProcessLauncher {

    private record TimeResult(double cpuTime, double clockTime, double memory) {}

    public static ExecutorResult execute(ProcessBuilder processBuilder,
                                         String stdinData,
                                         long timeLimitMs) throws IOException, InterruptedException {

        Process process = processBuilder.start();
        long start = System.nanoTime();

        // 三个线程共用的状态。
        // 必须用 atomic：普通字段跨线程没有可见性保证；
        // 而且 stdout/stderr 两个线程要同时往同一个计数器上加，
        // volatile long 会丢更新（读-改-写不是原子的）。
        AtomicLong totalBytes = new AtomicLong(0);
        AtomicBoolean outputExceeded = new AtomicBoolean(false);

        // 每个线程自己的输出桶，绝对不能共用：
        // ByteArrayOutputStream 不是线程安全的，而且 stdout 和 stderr 本来就要分开
        ByteArrayOutputStream stdoutBucket = new ByteArrayOutputStream();
        ByteArrayOutputStream stderrBucket = new ByteArrayOutputStream();

        // 三个线程必须在 waitFor 之前全部起来。
        // 顺序反了就是死锁：子进程写满管道缓冲区后阻塞在 write，
        // 父进程阻塞在 waitFor，双方互等，只能等超时强杀
        // 结果是一个本来能跑完的程序被判成 TLE
        Thread stdoutThread = new Thread(() ->
                pump(process, process.getInputStream(), stdoutBucket, totalBytes, outputExceeded));
        Thread stderrThread = new Thread(() ->
                pump(process, process.getErrorStream(), stderrBucket, totalBytes, outputExceeded));

        stdoutThread.setDaemon(true);
        stderrThread.setDaemon(true);

        stdoutThread.start();
        stderrThread.start();

        // stdin 是第三根管道，同样会写满。
        // 如果在这个线程原地写，主线程根本走不到 waitFor，超时就形同虚设。
        Thread stdinThread = null;
        if (stdinData != null) {
            stdinThread = new Thread(() -> feedStdin(process, stdinData));
            stdinThread.setDaemon(true);
            stdinThread.start();
        }

        boolean finished = process.waitFor(timeLimitMs, TimeUnit.MILLISECONDS);

        if (!finished) {
            // 这里的destroyForcibly只是通知了当前进程关闭 并不是立马结束
            process.destroyForcibly();
            // 必须等它真的结束：管道写端不关，pump 线程读不到 EOF，
            // 下面的 join 会一直挂着，等于把死锁从主线程搬到工作线程
            process.waitFor();
        }

        long end = System.nanoTime();
        long timeUsedMs = TimeUnit.NANOSECONDS.toMillis(end - start);

        // 进程正常结束 但是可能会出现 缓存池里的数据未完全读完 主线程需等待读线程的完成
        // 等所有读线程收工，这样拿到的桶和标志才是终值
        stdoutThread.join();
        stderrThread.join();

        if (stdinThread != null) {
            stdinThread.join();
        }

        // 进程已经确定退出了，exitValue 才是安全的。
        // 超时被强杀的那种，退出码是操作系统的垃圾值，用 -1 顶掉，
        // 反正调用方会先看 timedOut
        int exitCode = finished ? process.exitValue() : -1;

        return new ExecutorResult(
                exitCode,
                stdoutBucket.toString(StandardCharsets.UTF_8),
                stderrBucket.toString(StandardCharsets.UTF_8),
                !finished,
                (int) timeUsedMs,
                outputExceeded.get(),
                null);
    }
    private static void pump(Process process,
                             InputStream in,
                             ByteArrayOutputStream bucket,
                             AtomicLong totalBytes,
                             AtomicBoolean outputExceeded) {
        byte[] buffer = new byte[8192];
        try (in) {
            while (true) {
                int n = in.read(buffer);
                if (n == -1) {
                    break;
                }
                if (OUTPUT_LIMIT_BYTES > 0 && totalBytes.addAndGet(n) > OUTPUT_LIMIT_BYTES) {
                    outputExceeded.set(true);
                    process.destroyForcibly();
                    return;
                }
                bucket.write(buffer, 0, n);
            }
        } catch (IOException ignore) {
            // 进程被强杀或提前退出时 管道会断开，这是正常路径
            // TLE / OLE 都是我们主动杀进程，这个异常正是读线程的退出方式。
        }
    }
    private static void feedStdin(Process process,
                                  String stdinData) {
        try (OutputStream outputStream = process.getOutputStream()) {
            outputStream.write(stdinData.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignore) {
            // 同上：子进程被杀或提前退出时管道断开，属正常路径。
            // 而且 try-with-resources 保证流照样会被关掉，EOF 发得出去。
        }
    }


    public static List<ExecutorResult> executeAll(Path workDir, List<TestCase> testCases, int maxRunTimeMs, int maxMemoryLimitMb) throws IOException, InterruptedException {
        try (InputStream inputStream = SandboxProcessLauncher.class.getClassLoader()
                .getResourceAsStream("judge/run-cases.sh")) {
            if (inputStream == null) {
                throw new SandboxException("加载bash脚本失败!");
            }
            Files.write(workDir.resolve("run-cases.sh"), inputStream.readAllBytes());
        } 

        try {
            Path inputPath = Files.createDirectory(workDir.resolve("in"));
            for (int i = 0; i < testCases.size(); i++) {
                Files.writeString(inputPath.resolve((i + 1) + ".in"), testCases.get(i).getStdin());
            }
        } catch (IOException e) {
            throw new SandboxException("加载测试用例失败!");
        }

        int uid = (int) Files.getAttribute(workDir, "unix:uid");
        int gid = (int) Files.getAttribute(workDir, "unix:gid");

        List<String> command = List.of(
                "docker", "run", "--rm",
                "--network", "none",
                "--read-only", "--tmpfs", "/tmp",
                "--user", uid + ":" + gid,
                "--cpus", "1",
                "--memory", (maxMemoryLimitMb + 256) + "m",
                "--memory-swap", (maxMemoryLimitMb + 256) + "m",   // 同值 = 禁用 swap，否则熔断能超一倍
                "--pids-limit", "128",
                "-v", workDir.toAbsolutePath() + ":/work",
                "-w", "/work",
                "oj-judge:17",
                "bash", "/work/run-cases.sh",
                String.valueOf(testCases.size()),
                String.valueOf(maxRunTimeMs / 1000.0 + 0.5),
                String.valueOf(OUTPUT_LIMIT_BYTES / 1024),
                String.valueOf(maxMemoryLimitMb)
        );

        ProcessBuilder processBuilder = new ProcessBuilder(command);

        processBuilder.redirectErrorStream(true);
        processBuilder.redirectOutput(workDir.resolve("docker.log").toFile());

        int dockerExit = processBuilder.start().waitFor();

        // docker 自己失败 = 整条链路没跑起来，绝不能当成一组空结果
        if (dockerExit != 0) {
            throw new SandboxException("容器启动失败 docker exit=" + dockerExit
                    + "看" + workDir.resolve("docker.log"));
        }

        Path outDir = workDir.resolve("out");
        List<ExecutorResult> results = new ArrayList<>(testCases.size());
        for (int i = 1; i <= testCases.size(); i++) {
            int exitCode = Integer.parseInt(
                    Files.readString(outDir.resolve(i + ".exit")).trim());   // "124\n" 必须 trim

            byte[] out = Files.readAllBytes(outDir.resolve(i + ".out"));
            byte[] err = Files.readAllBytes(outDir.resolve(i + ".err"));
            byte[] time = Files.readAllBytes(outDir.resolve(i + ".time"));

            TimeResult timeResult = parseTimeResultFile(new String(time, StandardCharsets.UTF_8));

            int runTimeUsed = (int) Math.round(timeResult.clockTime * 1000);

            results.add(new ExecutorResult(
                    exitCode,
                    new String(out, StandardCharsets.UTF_8),
                    new String(err, StandardCharsets.UTF_8),
                    exitCode == 124 || runTimeUsed >= maxRunTimeMs,
                    runTimeUsed,
                    out.length >= OUTPUT_LIMIT_BYTES || err.length >= OUTPUT_LIMIT_BYTES,
                    (int) timeResult.memory
            ));
        }
        return results;
    }

    private static TimeResult parseTimeResultFile(String timeFile) {
        List<String> timeStr = Arrays.asList(timeFile.split("\t"));

        double cpuTime   = toDouble(getValue(timeStr, "User time")) + toDouble(getValue(timeStr, "System time"));
        double clockTime = toSeconds(getValue(timeStr, "Elapsed"));
        double memory    = toDouble(getValue(timeStr, "Maximum resident set size"));

        return new TimeResult(cpuTime, clockTime, memory);
    }
    private static String getValue(List<String> timeStr, String keyword) {
        for (String str : timeStr) {
            if (str.contains(keyword)) {
                return str.substring(str.lastIndexOf(": ") + 2).trim();
            }
        }
        return "";
    }

    // "0:01.09" / "0.32" / "1:02:03" 都吃 —— 前向累加，最后一段是秒
    private static double toSeconds(String value) {
        double total = 0;
        for (String part : value.split(":")) {
            total = total * 60 + toDouble(part);
        }
        return total;
    }

    // 空串/脏值给 0 —— 少一个数字，好过判不出题
    private static double toDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
