package com.chenru1chao.judge;

import com.chenru1chao.enums.JudgeStatus;
import com.chenru1chao.exception.SandboxException;
import com.chenru1chao.judge.model.CompileResult;
import com.chenru1chao.judge.model.ExecutorResult;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static com.chenru1chao.constant.SandboxLimits.*;
import static com.chenru1chao.constant.SandboxLimits.TIME_LIMIT_COMPILE_MS;
import static com.chenru1chao.judge.SandboxCleaner.deleteFile;
import static com.chenru1chao.judge.SandboxProcessLauncher.execute;

@Component
public class SandboxCompiler {
    public CompileResult compile(String sourceCode) {
        Path workDir;
        try {
            workDir = Files.createTempDirectory("oj-judge-");
        } catch (IOException e) {
            throw new SandboxException("测评机无法创建文件夹", e);
        }
        try {
            Path sourceFile = workDir.resolve("Main.java");
            Files.writeString(sourceFile, sourceCode, StandardCharsets.UTF_8);

            Path javaCompilePath = getJavaCompilePath();

            List<String> command = List.of(javaCompilePath.toString(),
                    "-encoding", "UTF-8",
                    "-Xmaxerrs", "20",
                    "-J-Dfile.encoding=UTF-8",
                    "-J-Xmx" + COMPILE_MEMORY_LIMIT_MB + "m",
                    "-J-XX:MaxMetaspaceSize=64m",
                    "-d", workDir.toString(), "Main.java");

            ProcessBuilder processBuilder = new ProcessBuilder(command);

            processBuilder.directory(workDir.toFile());

            ExecutorResult result = execute(processBuilder, null, TIME_LIMIT_COMPILE_MS);

            if (result.getTimedOut() || result.getExitCode() != 0) {
                deleteFile(workDir);
                return new CompileResult(JudgeStatus.COMPILE_ERROR,
                        result.getStdout(),
                        result.getStderr(),
                        workDir);
            }
            return new CompileResult(JudgeStatus.COMPILE_SUCCESS,
                    result.getStdout(),
                    result.getStderr(),
                    workDir);
        } catch (IOException | InterruptedException e) {
            deleteFile(workDir);
            throw new SandboxException("测评机子进程出现异常", e);
        }
    }

}
