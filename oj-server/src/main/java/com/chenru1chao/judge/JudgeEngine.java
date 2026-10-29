package com.chenru1chao.judge;

import com.chenru1chao.enums.JudgeStatus;
import com.chenru1chao.exception.JudgeException;
import com.chenru1chao.judge.model.JudgeExecutionBatch;
import com.chenru1chao.judge.model.JudgeCaseResult;
import com.chenru1chao.judge.model.JudgeResult;
import com.chenru1chao.judge.model.JudgeTestCase;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static com.chenru1chao.judge.JudgeWorkspaceCleaner.delete;
import static com.chenru1chao.judge.JudgeContainerRunner.executeAll;

@Component
public class JudgeEngine {

    // JVM内存溢出固定退出码
    private static final long OOM_CODE = 3;
    // 输出超限 128 + 25 = 153
    private static final long SIGXFSZ_CODE = 153;

    private static final long SIGKILL_CODE = 137;

    public JudgeResult judge(String sourceCode, List<JudgeTestCase> testCases, int maxRunTimeMs, int maxMemoryLimitMb) {

        Path workDir;
        try {
            workDir = Files.createTempDirectory("oj-judge-");
        } catch (IOException e) {
            throw new JudgeException("测评机无法创建文件夹", e);
        }
        try {
            Path sourceFile = workDir.resolve("Main.java");
            Files.writeString(sourceFile, sourceCode, StandardCharsets.UTF_8);
        } catch (IOException e) {
            delete(workDir);
            throw new JudgeException("测评机子进程出现异常", e);
        }

        int timeUsedMs = 0;
        int memoryUsedKb = 0;

        try {
            JudgeExecutionBatch batch = executeAll(workDir, testCases, maxRunTimeMs, maxMemoryLimitMb);

            // 编译失败是正常结果，不是异常：直接返回 CE，一个用例都不看
            if (batch.getCompileError() != null) {
                return new JudgeResult(JudgeStatus.COMPILE_ERROR, null, batch.getCompileError(),
                        null, null, null);
            }

            List<JudgeCaseResult> results = batch.getResults();
            for (int i = 0; i < results.size(); i++) {
                JudgeCaseResult result = results.get(i);
                JudgeStatus status = checkCase(result, testCases.get(i), maxMemoryLimitMb);
                if (status != null) {
                    if (status == JudgeStatus.MEMORY_LIMIT_EXCEEDED) {
                        // 堆溢这条路径上分配是被 JVM 直接拒掉的，一个字节都没进内存，
                        // 峰值 RSS 反而停在 JVM 底座（30MB 上下），显示出来和 AC 差不多。
                        // 改成报告上限，前端才能显示成「≥ 256 MB」这个门槛值
                        result.setMemoryUsedKb(maxMemoryLimitMb * 1024);
                    }
                    return failed(status, result, i + 1);
                }
                timeUsedMs = Math.max(timeUsedMs, result.getTimeUsedMs());
                memoryUsedKb = Math.max(memoryUsedKb, result.getMemoryUsedKb());
            }
        } catch (IOException | InterruptedException e) {
            throw new JudgeException("测评机运行时出现异常", e);
        } finally {
            delete(workDir);
        }
        return new JudgeResult(JudgeStatus.ACCEPTED, null, null, null, timeUsedMs, memoryUsedKb);
    }

    private JudgeStatus checkCase(JudgeCaseResult result, JudgeTestCase testCase, Integer memoryLimitMb) {

        // Tip: 如果忘记了就看这个地方！！！ 28秋招加油！！！
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
        if (!JudgeAnswerChecker.isAnswerCorrect(result.getStdout(), testCase.getStdout()))
            return JudgeStatus.WRONG_ANSWER;
        return null;
    }

    private JudgeResult failed(JudgeStatus status, JudgeCaseResult result, int caseIndex) {
        return new JudgeResult(status, result.getStdout(), result.getStderr(),
                caseIndex, result.getTimeUsedMs(), result.getMemoryUsedKb());
    }

}
