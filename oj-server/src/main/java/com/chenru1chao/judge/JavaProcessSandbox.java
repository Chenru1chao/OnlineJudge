package com.chenru1chao.judge;

import com.chenru1chao.entity.Problem;
import com.chenru1chao.enums.JudgeStatus;
import com.chenru1chao.judge.model.TestCase;
import com.chenru1chao.service.IProblemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.FileSystemUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static com.chenru1chao.constant.SandboxLimits.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class JavaProcessSandbox {
    // TODO: 后续迁移到Linux后妥善处理
    private static final long OOM_CODE = 3;

    private final IProblemService iProblemService;

    private record ExecResult(int exitCode,
                              String stdout,
                              String stderr,
                              boolean timedOut,
                              long timeUsedMs,
                              boolean outputExceeded) {}

    private record CompileResult(JudgeStatus judgeStatus,
                                 String stdout,
                                 String stderr,
                                 Path workDir) {}

    private record RunResult(JudgeStatus judgeStatus,
                                 String stdout,
                                 String stderr,
                                 int number,
                                 long timeUsedMs) {}

    private CompileResult compile(String sourceCode) throws IOException, InterruptedException {
        Path workDir = Files.createTempDirectory("oj-judge-");
        try {
            Path sourceFile = workDir.resolve("Main.java");
            Files.writeString(sourceFile, sourceCode, StandardCharsets.UTF_8);

            Path javaCompile = Path.of(JDK_HOME, "bin", IS_WINDOWS ? "javac.exe" : "javac");

            List<String> command = List.of(javaCompile.toString(),
                    "-encoding", "UTF-8",
                    "-J-Dfile.encoding=UTF-8",
                    "-J-Xmx" + COMPILE_MEMORY_LIMIT_MB + "m",
                    "-J-XX:MaxMetaspaceSize=64m",
                    "-d",
                    workDir.toString(),
                    "Main.java");

            ProcessBuilder processBuilder = new ProcessBuilder(command);

            processBuilder.directory(workDir.toFile());

            ExecResult result = execute(processBuilder, null, TIME_LIMIT_COMPILE_MS);

            if (result.timedOut() || result.exitCode() != 0) {
                return new CompileResult(JudgeStatus.COMPILE_ERROR,
                        result.stdout,
                        result.stderr,
                        workDir);
            }
            return new CompileResult(JudgeStatus.COMPILE_SUCCESS,
                    result.stdout,
                    result.stderr,
                    workDir);
        } catch (IOException | InterruptedException e) {
            deleteFile(workDir);
            throw e;
        }
    }

    private RunResult run(Path workDir, List<TestCase> testCases, long maxRunTimeMs, long maxMemoryLimitMb) throws IOException, InterruptedException {
        Path javaRun = Path.of(JDK_HOME, "bin", IS_WINDOWS ? "java.exe" : "java");

        List<String> command = List.of(javaRun.toString(),
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

        if (testCases.isEmpty()) {
            return new RunResult(JudgeStatus.UNKNOWN_ERROR, null, null, 0, -1);
        }

        long timeUsedMs = 0;

        for (int i = 0; i < testCases.size(); i++) {
            ExecResult result = execute(processBuilder, testCases.get(i).getStdin(), maxRunTimeMs);

            // 判断顺序不能改：OLE → TLE → RE → AC
            // 为什么 OLE 必须在最前面：输出超限是我们主动杀掉的进程，
            // 进程死了 waitFor 就返回 true，退出码还是强杀的垃圾值。
            // 先看 exitCode 的话会被带偏判成 RUNTIME_ERROR。
            if (result.outputExceeded()) {
                return new RunResult(JudgeStatus.OUTPUT_LIMIT_EXCEEDED, result.stdout(), result.stderr(), i + 1, result.timeUsedMs);
            }
            if (result.timedOut()) {
                return new RunResult(JudgeStatus.TIME_LIMIT_EXCEEDED, result.stdout(), result.stderr(), i + 1, result.timeUsedMs);
            }
            if (result.exitCode == OOM_CODE) {
                return new RunResult(JudgeStatus.MEMORY_LIMIT_EXCEEDED, result.stdout(), result.stderr(), i + 1, result.timeUsedMs);
            }
            if (result.exitCode() != 0) {
                return new RunResult(JudgeStatus.RUNTIME_ERROR, result.stdout(), result.stderr(), i + 1, result.timeUsedMs);
            }
            if (!MultiTestValidator.answerValidator(result.stdout, testCases.get(i).getStdout())) {
                return new RunResult(JudgeStatus.WRONG_ANSWER, result.stdout(), result.stderr(), i + 1, result.timeUsedMs);
            }
            timeUsedMs = Math.max(timeUsedMs, result.timeUsedMs);
        }

        // 这里全部ac返回当前的总题数
        return new RunResult(JudgeStatus.ACCEPTED, null, null, testCases.size(), timeUsedMs);
    }

    private ExecResult execute(ProcessBuilder processBuilder,
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

        return new ExecResult(
                exitCode,
                stdoutBucket.toString(StandardCharsets.UTF_8),
                stderrBucket.toString(StandardCharsets.UTF_8),
                !finished,
                timeUsedMs,
                outputExceeded.get());
    }
    private void pump(Process process,
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
                if (com.chenru1chao.constant.SandboxLimits.OUTPUT_LIMIT_BYTES > 0 && totalBytes.addAndGet(n) > com.chenru1chao.constant.SandboxLimits.OUTPUT_LIMIT_BYTES) {
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

    /**
     * 把测试数据写进子进程的 stdin，然后关掉流。
     * 关流 = 发 EOF，子进程靠它知道输入结束了
     * （像 while (sc.hasNextInt()) 这种写法，没有 EOF 会永远卡住）。
     */
    private void feedStdin(Process process, String stdinData) {
        try (OutputStream outputStream = process.getOutputStream()) {
            outputStream.write(stdinData.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignore) {
            // 同上：子进程被杀或提前退出时管道断开，属正常路径。
            // 而且 try-with-resources 保证流照样会被关掉，EOF 发得出去。
        }
    }

    private void deleteFile(Path workDir) {
        if (workDir != null) {
            try {
                FileSystemUtils.deleteRecursively(workDir);
            } catch (IOException e) {
                // TODO: 这里不能再抛了 会吞异常用日志记录 先用控制台 后续完善保存到日志文件里 人工介入删除
                log.error("{}:文件删除失败", workDir);
            }
        }
    }

    public void compileAndRun(Integer problemId, String code, List<TestCase> testCases) {
        CompileResult compileResult = null;
        try {
            Problem problem = iProblemService.query().eq("id", problemId).one();

            compileResult = compile(code);

            if (compileResult.judgeStatus.getCode() != JudgeStatus.COMPILE_SUCCESS.getCode()) {
                System.out.println(compileResult.judgeStatus);
                System.out.println(compileResult.stderr.replace(compileResult.workDir.toString(), ""));
                return;
            }

            RunResult runResult = run(compileResult.workDir,
                    testCases,
                    problem.getTimeLimit(),
                    problem.getMemoryLimit());

            if (runResult.judgeStatus() == JudgeStatus.ACCEPTED) {
                System.out.println(runResult.judgeStatus());
                System.out.println(runResult.timeUsedMs + "ms");
            } else {
                System.out.println("第" + runResult.number() + "个测试用例" + runResult.judgeStatus());
                System.out.println(runResult.stderr);
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            if (compileResult != null)
                deleteFile(compileResult.workDir);
        }
    }
}
