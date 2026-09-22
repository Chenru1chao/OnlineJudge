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

import static com.chenru1chao.constant.SandboxLimits.*;
import static com.chenru1chao.judge.SandboxCleaner.deleteFile;
import static com.chenru1chao.judge.SandboxProcessLauncher.execute;

@Component
public class SandboxRunner {

    // TODO: 后续迁移到Linux后妥善处理
    private static final long OOM_CODE = 3;

    public RunResult run(Path workDir, List<TestCase> testCases, int maxRunTimeMs, int maxMemoryLimitMb) {
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
    }

}
