package com.chenru1chao.judge;

import enums.JudgeStatus;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
public class JavaProcessSandbox {

    // 获取当前JDK的文件地址 C:\Users\陈睿超\.jdks\temurin-17.0.19
    private static final String javaHome = System.getProperty("java.home");
    // 判断当前是否是windows系统 Windows 11
    private static final boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
    // 程序的最大运行时间
    private static final long TIME_LIMIT_RUNTIME = 2000;
    // 程序的最大编译时间
    private static final long TIME_LIMIT_COMPILE = 5000;
    // 运行时输出上限 128MB，超过判 OLE
    private static final long OUTPUT_LIMIT_RUNTIME = 128L * 1024 * 1024;
    // 编译不限流（0 = 关掉）。javac 输出本来就有上限，
    // 而且真超了也该判 CE 而不是 OLE，不然提交者会一脸问号
    // TODO: 后续需妥善处理
    private static final long OOM_CODE = 3;

    /**
     * 一次进程执行的原始结果。
     * 它只描述"发生了什么"，不描述"该判什么"——判什么是 compile/run 自己的事。
     */
    private record ExecResult(int exitCode,
                              String stdout,
                              String stderr,
                              boolean timedOut,
                              boolean outputExceeded) {}

    private record CompileResult(JudgeStatus judgeStatus,
                                 String stdout,
                                 String stderr,
                                 Path workDir) {}

    private record RunResult(JudgeStatus judgeStatus,
                                 String stdout,
                                 String stderr,
                                 int number) {}

    private record TestCase(String stdin, String stdout) {
        TestCase {
            if (stdin == null) stdin = "";
        }
    }

    public static CompileResult compile(String sourceCode, long maxCompileTimeMs, long maxOutputBytes) throws IOException, InterruptedException {
        // 在系统盘的Temp临时文件区创建一个文件 指定前缀并且随机生成文件名
        Path workDir = Files.createTempDirectory("oj-judge-");
        // resolve就是路径拼接
        Path sourceFile = workDir.resolve("Main.java");
        Files.writeString(sourceFile, sourceCode, StandardCharsets.UTF_8);

        // 拼接获取当前的JavaCompile工具的地址 后续编译命令会用到
        Path javaCompile = Path.of(javaHome, "bin", isWindows ? "javac.exe" : "javac");

        // 拼接编译的命令
        List<String> command = List.of(javaCompile.toString(),
                "-encoding", "UTF-8",
                "-J-Dfile.encoding=UTF-8",
                "-J-Xmx" + (maxOutputBytes / (1024 * 1024)) + "m",
                "-J-XX:MaxMetaspaceSize=64m",
                "-d",
                workDir.toString(),
                "Main.java");

        // 创建进程来处理当前的命令
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        // 指定当前命名的运行所在文件夹 类似于cd 进入文件夹
        processBuilder.directory(workDir.toFile());

        // 编译不需要 stdin，传 null
        ExecResult result = execute(processBuilder, null, maxCompileTimeMs, maxOutputBytes);

        // 编译只看两件事：超时了没、退出码是不是 0
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
    }

    public static RunResult run(Path workDir, List<TestCase> testCases, long maxRunTimeMs, long maxOutputBytes) throws IOException, InterruptedException {
        Path javaRun = Path.of(javaHome, "bin", isWindows ? "java.exe" : "java");

        List<String> command = List.of(javaRun.toString(),
                "-Dfile.encoding=UTF-8",
                "-Xmx" + (maxOutputBytes / (1024 * 1024)) + "m",
                "-XX:MaxMetaspaceSize=64m",
                "-Xss1m",
                "-XX:+UseSerialGC",
                "-XX:+ExitOnOutOfMemoryError",
                "-XX:TieredStopAtLevel=1",
                "-cp",
                workDir.toString(),
                "Main");

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(workDir.toFile());

        if (testCases.isEmpty()) {
            return new RunResult(JudgeStatus.UNKNOWN_ERROR, null, null, 0);
        }

        for (int i = 0; i < testCases.size(); i++) {
            ExecResult result = execute(processBuilder, testCases.get(i).stdin, maxRunTimeMs, maxOutputBytes);

            // 判断顺序不能改：OLE → TLE → RE → AC
            // 为什么 OLE 必须在最前面：输出超限是我们主动杀掉的进程，
            // 进程死了 waitFor 就返回 true，退出码还是强杀的垃圾值。
            // 先看 exitCode 的话会被带偏判成 RUNTIME_ERROR。
            if (result.outputExceeded()) {
                return new RunResult(JudgeStatus.OUTPUT_LIMIT_EXCEEDED, result.stdout(), result.stderr(), i + 1);
            }
            if (result.timedOut()) {
                return new RunResult(JudgeStatus.TIME_LIMIT_EXCEEDED, result.stdout(), result.stderr(), i + 1);
            }
            if (result.exitCode == OOM_CODE) {
                return new RunResult(JudgeStatus.MEMORY_LIMIT_EXCEEDED, result.stdout(), result.stderr(), i + 1);
            }
            if (result.exitCode() != 0) {
                return new RunResult(JudgeStatus.RUNTIME_ERROR, result.stdout(), result.stderr(), i + 1);
            }
            if (!MultiTestValidator.answerValidator(result.stdout, testCases.get(i).stdout)) {
                return new RunResult(JudgeStatus.WRONG_ANSWER, result.stdout(), result.stderr(), i + 1);
            }
        }

        // 这里全部ac返回当前的总题数
        return new RunResult(JudgeStatus.ACCEPTED, null, null, testCases.size());
    }

    /**
     * 起进程、抽干管道、限时、拿退出码。编译和运行共用这一段，
     * 所以不可能再出现"修了一份忘了另一份"的情况 抽取公共逻辑。
     *
     * @param stdinData        null 表示不写 stdin（编译场景）
     * @param timeLimitMs      超时毫秒数
     * @param outputLimitBytes 输出上限字节数；<= 0 表示不限流
     */
    private static ExecResult execute(ProcessBuilder processBuilder,
                                      String stdinData,
                                      long timeLimitMs,
                                      long outputLimitBytes) throws IOException, InterruptedException {

        Process process = processBuilder.start();

        // 三个线程【共用】的状态。
        // 必须用 atomic：普通字段跨线程没有可见性保证；
        // 而且 stdout/stderr 两个线程要同时往同一个计数器上加，
        // volatile long 会丢更新（读-改-写不是原子的）。
        AtomicLong totalBytes = new AtomicLong(0);
        AtomicBoolean outputExceeded = new AtomicBoolean(false);

        // 每个线程自己的输出桶，【绝对不能共用】：
        // ByteArrayOutputStream 不是线程安全的，而且 stdout 和 stderr 本来就要分开
        ByteArrayOutputStream stdoutBucket = new ByteArrayOutputStream();
        ByteArrayOutputStream stderrBucket = new ByteArrayOutputStream();

        // 三个线程必须在 waitFor 之前全部起来。
        // 顺序反了就是死锁：子进程写满管道缓冲区后阻塞在 write，
        // 父进程阻塞在 waitFor，双方互等，只能等超时强杀，
        // 结果是一个本来能跑完的程序被判成 TLE。
        Thread stdoutThread = new Thread(() ->
                pump(process, process.getInputStream(), stdoutBucket, totalBytes, outputExceeded, outputLimitBytes));
        Thread stderrThread = new Thread(() ->
                pump(process, process.getErrorStream(), stderrBucket, totalBytes, outputExceeded, outputLimitBytes));

        stdoutThread.setDaemon(true);
        stderrThread.setDaemon(true);
        stdoutThread.start();
        stderrThread.start();

        // stdin 是【第三根】管道，同样会写满。
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

        // 进程正常结束 但是可能会出现 缓存池里的数据未完全读完 主线程需等待读线程的完成
        // 等所有读线程收工，这样拿到的桶和标志才是【终值】
        stdoutThread.join();
        stderrThread.join();
        if (stdinThread != null) {
            stdinThread.join();
        }

        // 进程已经确定退出了，exitValue 才是安全的。
        // 超时被强杀的那种，退出码是操作系统的垃圾值，用 -1 顶掉，
        // 反正调用方会先看 timedOut
        int exitCode = finished ? process.exitValue() : -1;

        // TODO: debug用的 后续应该删掉
        // System.out.println("返回值:" + exitCode);

        return new ExecResult(
                exitCode,
                stdoutBucket.toString(StandardCharsets.UTF_8),
                stderrBucket.toString(StandardCharsets.UTF_8),
                !finished,
                outputExceeded.get());
    }

    /**
     * 把子进程的一根输出管道抽干。
     * 边写边读：读到的原始字节先攒进桶里，最后一次性解码。
     * 【不要】逐块 new String(buffer, 0, n, UTF_8)：一个汉字占 3 字节，
     * 跨块被切开的话两块都会解出乱码，拼不回来。要一次全部取出并且解码
     */
    private static void pump(Process process,
                             InputStream in,
                             ByteArrayOutputStream bucket,
                             AtomicLong totalBytes,
                             AtomicBoolean outputExceeded,
                             long outputLimitBytes) {
        byte[] buffer = new byte[8192];
        try (in) {
            while (true) {
                int n = in.read(buffer);
                if (n == -1) {
                    break;      // 这里是这样的 只有当前进程结束了并且缓冲池里不读到了跳出循环
                }
                // 加完顺便比一下，一步完成
                if (outputLimitBytes > 0 && totalBytes.addAndGet(n) > outputLimitBytes) {
                    outputExceeded.set(true);
                    process.destroyForcibly();   // 把主线程从 waitFor 里叫醒
                    return;                      // 停止读，别再往内存里堆
                }
                // 注意是三参数版本：只写 buffer 的 [0, n) 这一段
                bucket.write(buffer, 0, n);
            }
        } catch (IOException ignore) {
            // 进程被强杀或提前退出时 管道会断开，这是【正常路径】：
            // TLE / OLE 都是我们主动杀进程，这个异常正是读线程的退出方式。
        }
    }

    /**
     * 把测试数据写进子进程的 stdin，然后关掉流。
     * 关流 = 发 EOF，子进程靠它知道输入结束了
     * （像 while (sc.hasNextInt()) 这种写法，没有 EOF 会永远卡住）。
     */
    private static void feedStdin(Process process, String stdinData) {
        try (OutputStream outputStream = process.getOutputStream()) {
            outputStream.write(stdinData.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignore) {
            // 同上：子进程被杀或提前退出时管道断开，属正常路径。
            // 而且 try-with-resources 保证流照样会被关掉，EOF 发得出去。
        }
    }

    public static void main(String[] args) {
        CompileResult compileResult = null;
        try {
            // TODO: 获取当前题目的限制条件的方法
            compileResult = compile("""
                    import java.util.Scanner;
    
                    public class Main {
                        public static void main(String[] args) {
                            int x = 1 / 0;
                        }
                    }
                    """, TIME_LIMIT_COMPILE, OUTPUT_LIMIT_RUNTIME);

            if (compileResult.judgeStatus.getCode() != JudgeStatus.COMPILE_SUCCESS.getCode()) {
                System.out.println(compileResult.judgeStatus);
                System.out.println(compileResult.stderr.replace(compileResult.workDir.toString(), ""));
                return;
            }

            // TODO: 获取测试用例的方法
            // List.of(new TestCase("1 2", "3"), new TestCase("1000", "2000")
            RunResult runResult = run(compileResult.workDir,
                    List.of(new TestCase("", "hello chenru1chao")),
                    TIME_LIMIT_RUNTIME,
                    OUTPUT_LIMIT_RUNTIME);
            if (runResult.judgeStatus() == JudgeStatus.ACCEPTED)
                System.out.println(runResult.judgeStatus());
            else {
                System.out.println("第" + runResult.number() + "个测试用例" + runResult.judgeStatus());
                System.out.println(runResult.stderr);
            }

        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            if (compileResult != null && compileResult.workDir != null) {
                try {
                    FileSystemUtils.deleteRecursively(compileResult.workDir);
                } catch (IOException e) {
                    // TODO: 这里不能再抛了 会吞异常用日志记录 先用控制台 后续完善保存到日志文件里 人工介入删除
                    log.error("{}:文件删除失败", compileResult.workDir);
                }
            }
        }
    }
}
