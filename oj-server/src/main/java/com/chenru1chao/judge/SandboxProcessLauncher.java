package com.chenru1chao.judge;

import com.chenru1chao.judge.model.ExecutorResult;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class SandboxProcessLauncher {

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
                outputExceeded.get());
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
    private static void feedStdin(Process process, String stdinData) {
        try (OutputStream outputStream = process.getOutputStream()) {
            outputStream.write(stdinData.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignore) {
            // 同上：子进程被杀或提前退出时管道断开，属正常路径。
            // 而且 try-with-resources 保证流照样会被关掉，EOF 发得出去。
        }
    }
}
