package com.chenru1chao.judge;

import com.chenru1chao.exception.JudgeException;
import com.chenru1chao.judge.model.JudgeExecutionBatch;
import com.chenru1chao.judge.model.JudgeCaseResult;
import com.chenru1chao.judge.model.JudgeTestCase;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.chenru1chao.constant.JudgeLimits.COMPILE_MEMORY_LIMIT_MB;
import static com.chenru1chao.constant.JudgeLimits.OUTPUT_LIMIT_BYTES;
import static com.chenru1chao.constant.JudgeLimits.TIME_LIMIT_COMPILE_MS;

@Slf4j
public class JudgeContainerRunner {

    private record TimeResult(double cpuTime, double clockTime, double memory) {}

    public static JudgeExecutionBatch executeAll(Path workDir, List<JudgeTestCase> testCases, int maxRunTimeMs, int maxMemoryLimitMb) throws IOException, InterruptedException {
        try (InputStream inputStream = JudgeContainerRunner.class.getClassLoader()
                .getResourceAsStream("judge/run-cases.sh")) {
            if (inputStream == null) {
                throw new JudgeException("加载bash脚本失败!");
            }
            BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream);
            Files.write(workDir.resolve("run-cases.sh"), bufferedInputStream.readAllBytes());
        } 

        try {
            // 保存测试用例到用户的临时文件夹中
            Path inputPath = Files.createDirectory(workDir.resolve("in"));
            // 创建目录保存程序输出
            Files.createDirectory(workDir.resolve("out"));
            for (int i = 0; i < testCases.size(); i++) {
                Files.writeString(inputPath.resolve((i + 1) + ".in"), testCases.get(i).getStdin());
            }
        } catch (IOException e) {
            throw new JudgeException("加载测试用例失败!");
        }

        // 利用临时文件的文件夹的文件名作为容器名 便于后续容器启动失败删除容器
        String containerName = workDir.getFileName().toString();

        // 编译和运行在同一个容器里，内存要按两者的较大值给。
        // 运行期判 MLE 靠的是 JVM 的 -Xmx + ExitOnOutOfMemoryError（退出码 3），
        // cgroup 只是兜底 137，所以这里给大了不会把 MLE 判成 AC。
        int containerMemoryMb = Math.max(maxMemoryLimitMb + 256, (int) COMPILE_MEMORY_LIMIT_MB + 256);

        List<String> command = List.of(
                "docker", "run", "--rm",
                "--name", containerName,
                "--network", "none",
                "--read-only",
                "--tmpfs", "/tmp",
                "--cpus", "1",
                "--memory", containerMemoryMb + "m",
                "--memory-swap", containerMemoryMb + "m",   // 同值 = 禁用 swap
                "--pids-limit", "128", // 限制用户的线程数 防止无限创建线程 同时给jvm的多线程留余量
                "-v", workDir.toAbsolutePath() + ":/work", // 挂载
                "-w", "/work",
                "oj-judge:17",
                "bash", "/work/run-cases.sh",
                String.valueOf(testCases.size()),
                String.valueOf(maxRunTimeMs / 1000.0 + 0.5),
                String.valueOf(OUTPUT_LIMIT_BYTES / 1024),
                String.valueOf(maxMemoryLimitMb),
                String.valueOf(COMPILE_MEMORY_LIMIT_MB),
                String.valueOf(TIME_LIMIT_COMPILE_MS / 1000)
        );

        ProcessBuilder processBuilder = new ProcessBuilder(command);

        processBuilder.redirectErrorStream(true);
        processBuilder.redirectOutput(workDir.resolve("docker.log").toFile());

        // 预算里要带上编译时间，否则编译还没完就被当成判题超时销毁容器
        long budgetMs = (long) testCases.size() * maxRunTimeMs + TIME_LIMIT_COMPILE_MS + 5_000L;

        Process process = processBuilder.start();

        // 子进程限时 防止docker启动出现异常 导致线程卡是
        boolean finished = process.waitFor(budgetMs, TimeUnit.MILLISECONDS);

        if (!finished) {
            // 发起销毁进程命令
            process.destroyForcibly();
            // 等待进程的销毁完成
            process.waitFor(5, TimeUnit.SECONDS);

            try {
                ProcessBuilder destroyProcess = new ProcessBuilder(List.of("docker", "rm", "-f", containerName));
                boolean isDestroyFinished = destroyProcess.start().waitFor(10, TimeUnit.SECONDS);
                if (!isDestroyFinished) {
                    log.warn("容器销毁超时 容器名称:{}", containerName);
                }
            } catch (Exception ignore) {
                // 这里不能抛出异常 会覆盖下方真正的异常信息
                log.error("容器销毁失败 容器名称:{}", containerName, ignore);
            }

            throw new JudgeException("判题容器超时未返回 budget=" + budgetMs + "ms 日志见 " +
                    workDir.resolve("docker.log"));
        }


        int dockerExit = process.exitValue();
        Path outDir = workDir.resolve("out");

        // 编译结果先判。编译失败时脚本会带着非 0 退出，dockerExit 也是非 0，
        // 但那是用户的 CE，不是链路故障 —— 所以这里必须排在 dockerExit 检查前面。
        Path compileExitPath = outDir.resolve("compile.exit");
        if (!Files.exists(compileExitPath)) {
            // 脚本根本没跑到编译那一步（docker 起不来、挂载错），这是测评机的问题，
            // 不能当成 CE 糊弄用户
            throw new JudgeException("编译阶段没有产出 compile.exit docker exit=" + dockerExit
                    + " 看" + workDir.resolve("docker.log"));
        }

        String compileExit = Files.readString(compileExitPath).trim();
        if (!"0".equals(compileExit)) {
            Path compileErrPath = outDir.resolve("compile.err");
            String compileErr = Files.exists(compileErrPath)
                    ? Files.readString(compileErrPath) : "编译失败，但没有拿到 javac 报错";
            return new JudgeExecutionBatch(compileErr, List.of());
        }

        // docker 自己失败 = 整条链路没跑起来，绝不能当成一组空结果
        if (dockerExit != 0) {
            throw new JudgeException("容器启动失败 docker exit=" + dockerExit
                    + "看" + workDir.resolve("docker.log"));
        }


        List<JudgeCaseResult> results = new ArrayList<>(testCases.size());
        for (int i = 1; i <= testCases.size(); i++) {
            int exitCode = Integer.parseInt(
                    Files.readString(outDir.resolve(i + ".exit")).trim());   // "124\n" 必须 trim

            byte[] out = Files.readAllBytes(outDir.resolve(i + ".out"));
            byte[] err = Files.readAllBytes(outDir.resolve(i + ".err"));
            byte[] time = Files.readAllBytes(outDir.resolve(i + ".time"));

            TimeResult timeResult = parseTimeResultFile(new String(time, StandardCharsets.UTF_8));

            int runTimeUsed = (int) Math.round(timeResult.clockTime * 1000);

            results.add(new JudgeCaseResult(
                    exitCode,
                    new String(out, StandardCharsets.UTF_8),
                    new String(err, StandardCharsets.UTF_8),
                    exitCode == 124 || runTimeUsed >= maxRunTimeMs,
                    runTimeUsed,
                    out.length >= OUTPUT_LIMIT_BYTES || err.length >= OUTPUT_LIMIT_BYTES,
                    (int) timeResult.memory
            ));
        }
        return new JudgeExecutionBatch(null, results);
    }

    private static TimeResult parseTimeResultFile(String timeFile) {
        List<String> timeStr = Arrays.asList(timeFile.split("\t"));

        double cpuTime = toDouble(getValue(timeStr, "User time")) + toDouble(getValue(timeStr, "System time"));
        double clockTime = toSeconds(getValue(timeStr, "Elapsed"));
        double memory = toDouble(getValue(timeStr, "Maximum resident set size"));

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
