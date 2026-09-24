package com.chenru1chao.judge;

import com.chenru1chao.config.TestCaseConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProblemFileReader {

    private static final String PROBLEM_ID_PLACEHOLDER = "problemId";

    private final TestCaseConfig testCaseConfig;

    /**
     * 解析某个题目的文件目录。
     * 比如配置是 D:/develop/OnlineJudge/problemId/，problemId=1 就得到 D:/develop/OnlineJudge/1/
     */
    public String resolveDir(Integer problemId) {
        String prefix = testCaseConfig.getPrefix() == null ? "" : testCaseConfig.getPrefix();

        if (!prefix.contains(PROBLEM_ID_PLACEHOLDER)) {
            // 配置里没写占位符，replace 不会有任何效果，只能按 JVM 工作目录读相对路径。
            // 本地开发不会走到这里（是 D:/develop/OnlineJudge/problemId/），Docker 里 prefix 为空串会走到。
            log.warn("题目文件目录里没有 '{}' 占位符，当前配置：[{}] —— 将按相对路径读取题目文件",
                    PROBLEM_ID_PLACEHOLDER, prefix);
        }

        return prefix.replace(PROBLEM_ID_PLACEHOLDER, problemId.toString());
    }

    /**
     * 读一个题目文件的内容。
     *
     * @return 文件内容；文件不存在或读不出来返回 null，交给调用方决定是报错还是跳过
     */
    public String readIfExists(String dir, String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }

        Path path = Path.of(dir + fileName);
        try {
            // 用 readString 而不是 new String(readAllBytes())：
            // 后者按平台默认编码解码，中文 Windows 上是 GBK，读 UTF-8 用例会变乱码
            return Files.readString(path);
        } catch (IOException e) {
            log.warn("题目文件读取失败: {}", path, e);
            return null;
        }
    }
}
