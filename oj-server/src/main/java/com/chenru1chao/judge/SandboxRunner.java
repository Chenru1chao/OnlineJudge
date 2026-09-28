package com.chenru1chao.judge;

import com.chenru1chao.enums.JudgeStatus;
import com.chenru1chao.exception.SandboxException;
import com.chenru1chao.judge.model.ExecutorResult;
import com.chenru1chao.judge.model.RunResult;
import com.chenru1chao.judge.model.TestCase;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static com.chenru1chao.judge.SandboxCleaner.deleteFile;
import static com.chenru1chao.judge.SandboxProcessLauncher.executeAll;

@Component
public class SandboxRunner {

    // JVM内存溢出固定退出码
    private static final long OOM_CODE = 3;
    // 输出超限 128 + 25 = 153
    private static final long SIGXFSZ_CODE = 153;

    private static final long SIGKILL_CODE = 137;

    // 过时: 创建子进程跑用户程序 子进程用户相同权限 无法有效限制用户行为
    /*public RunResult run(Path workDir, List<TestCase> testCases, int maxRunTimeMs, int maxMemoryLimitMb) {
        int timeUsedMs = 0;
        try {
            Path jdkPath = getJDKPath();

            List<String> command = List.of(jdkPath.toString(),
                    "-Dfile.encoding=UTF-8",
                    "-Xmx" + maxMemoryLimitMb + "m",
                    "-XX:MaxMetaspaceSize=64m",
                    "-Xss1m",
                    "-XX:+UseSerialGC",
                    "-XX:TieredStopAtLevel=1",
                    "-XX:+ExitOnOutOfMemoryError",
                    "-cp",
                    workDir.toString(),
                    "Main");

            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.directory(workDir.toFile());

            for (int i = 0; i < testCases.size(); i++) {
                ExecutorResult result = execute(processBuilder, testCases.get(i).getStdin(), maxRunTimeMs);

                // 判断顺序不能改：OLE → TLE → RE → AC
                // 为什么 OLE 必须在最前面：输出超限是我们主动杀掉的进程，
                // 进程死了 waitFor 就返回 true，退出码还是强杀的垃圾值。
                // 先看 exitCode 的话会被带偏判成 RUNTIME_ERROR。
                if (result.getOutputExceeded()) {
                    return new RunResult(JudgeStatus.OUTPUT_LIMIT_EXCEEDED, result.getStdout(), result.getStderr(), i + 1, result.getTimeUsedMs());
                }
                if (result.getTimedOut()) {
                    return new RunResult(JudgeStatus.TIME_LIMIT_EXCEEDED, result.getStdout(), result.getStderr(), i + 1, result.getTimeUsedMs());
                }
                if (result.getExitCode() == OOM_CODE) {
                    return new RunResult(JudgeStatus.MEMORY_LIMIT_EXCEEDED, result.getStdout(), result.getStderr(), i + 1, result.getTimeUsedMs());
                }
                if (result.getExitCode() != 0) {
                    return new RunResult(JudgeStatus.RUNTIME_ERROR, result.getStdout(), result.getStderr(), i + 1, result.getTimeUsedMs());
                }
                if (!SandboxMultiTestValidator.answerValidator(result.getStdout(), testCases.get(i).getStdout())) {
                    return new RunResult(JudgeStatus.WRONG_ANSWER, result.getStdout(), result.getStderr(), i + 1, result.getTimeUsedMs());
                }
                timeUsedMs = Math.max(timeUsedMs, result.getTimeUsedMs());
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new SandboxException("测评机出现异常", e);
        } finally {
            deleteFile(workDir);
        }
        // 这里全部ac返回当前的总题数
        return new RunResult(JudgeStatus.ACCEPTED, null, null, null, timeUsedMs);
    }*/

    public RunResult run(Path workDir, List<TestCase> testCases, int maxRunTimeMs, int maxMemoryLimitMb) {
        int timeUsedMs = 0;
        int memoryUsedKb = 0;
        try {
            List<ExecutorResult> results = executeAll(workDir, testCases, maxRunTimeMs, maxMemoryLimitMb);
            for (int i = 0; i < results.size(); i++) {
                ExecutorResult result = results.get(i);
                JudgeStatus status = judge(result, testCases.get(i), maxMemoryLimitMb);
                if (status != null) {
                    return failed(status, result, i + 1);
                }
                timeUsedMs = Math.max(timeUsedMs, result.getTimeUsedMs());
                memoryUsedKb = Math.max(memoryUsedKb, result.getMemoryUsedKb());
            }
        } catch (IOException | InterruptedException e) {
            throw new SandboxException("测评机出现异常", e);
        } finally {
            deleteFile(workDir);
        }
        return new RunResult(JudgeStatus.ACCEPTED, null, null, null, timeUsedMs, memoryUsedKb);
    }

    private JudgeStatus judge(ExecutorResult result, TestCase testCase, Integer memoryLimitMb) {

        // Tip: 如果忘记了就看这个地方！！！ 27秋招加油！！！
        // 首先为什么要固定顺序？ 我们每次主动杀死进程得到的返回值可能是垃圾值
        // 但是如果是程序超出了环境的限制 jvm内存溢出jvm杀死程序退出值是3
        // 超时tle被timeout关闭得到124 timeout兜底 timeout程序启动需要时间我们给的远比启动耗时更多的时间
        // timeout只是兜底 没被杀死不代表没有超时 bash脚本将程序的执行信息写入time文件
        // 利用cpu时间和挂钟时间判断 这里cpu时间有误差多线程的因故 采用放宽时间用挂钟判断 挂钟时间会算上JVM启动时间
        // 输出限制ole 我们这里做了双重判断 程序结束判断输出文件大小 bash程序限制因为输出超限被杀得到153
        // JVM堆外溢出元空间、线程栈、直接内存 撑爆的是容器的cgroup上限 Docker的OOM直接SIGKILL掉java 退出码 137
        // 其他的如果是因为用户的程序抛出了运行时异常就是一个彻底的垃圾值 运行成功结束得到0
        // 程序得成功执行结束返回0 判WA才有意义 所以必须放在最后一个来判断

        if (result.getOutputExceeded() || result.getExitCode() == SIGXFSZ_CODE)
            return JudgeStatus.OUTPUT_LIMIT_EXCEEDED;
        if (result.getTimedOut())
            return JudgeStatus.TIME_LIMIT_EXCEEDED;
        if (result.getExitCode() == OOM_CODE)
            return JudgeStatus.MEMORY_LIMIT_EXCEEDED;
        if (result.getExitCode() == SIGKILL_CODE) {
            return result.getMemoryUsedKb() != null && result.getMemoryUsedKb() >= memoryLimitMb * 1024L
                    ? JudgeStatus.MEMORY_LIMIT_EXCEEDED : JudgeStatus.RUNTIME_ERROR;
        }
        if (result.getExitCode() != 0)
            return JudgeStatus.RUNTIME_ERROR;
        if (!SandboxMultiTestValidator.answerValidator(result.getStdout(), testCase.getStdout()))
            return JudgeStatus.WRONG_ANSWER;
        return null;
    }

    private RunResult failed(JudgeStatus status, ExecutorResult result, int caseIndex) {
        return new RunResult(status, result.getStdout(), result.getStderr(),
                caseIndex, result.getTimeUsedMs(), result.getMemoryUsedKb());
    }

}
