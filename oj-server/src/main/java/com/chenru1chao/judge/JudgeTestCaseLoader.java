package com.chenru1chao.judge;

import com.chenru1chao.entity.ProblemTestCase;
import com.chenru1chao.exception.JudgeException;
import com.chenru1chao.judge.model.JudgeTestCase;
import com.chenru1chao.service.IProblemTestCaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JudgeTestCaseLoader {

    private final JudgeProblemFileReader problemFileReader;
    private final IProblemTestCaseService iProblemTestCaseService;

    public List<JudgeTestCase> loadTestCases(Integer problemId) {
        // 必须按 sort 排：判题顺序不稳定的话 报出来的 failedCaseNo 每次都不一样
        // 存储的时候不一定能保证有序 所以添加了一个字段sort来排序
        List<ProblemTestCase> testCaseFiles = iProblemTestCaseService.lambdaQuery()
                .eq(ProblemTestCase::getProblemId, problemId)
                .orderByAsc(ProblemTestCase::getSort)
                .list();

        ArrayList<JudgeTestCase> testCases = new ArrayList<>(testCaseFiles.size());

        // 动态解析获取当前题目存储的测试用例路径 replace替换
        String dir = problemFileReader.resolveDir(problemId);

        for (ProblemTestCase testCase : testCaseFiles) {
            testCases.add(new JudgeTestCase(
                    readFile(dir, testCase.getInputFile()),
                    readFile(dir, testCase.getOutputFile())));
        }

        return testCases;
    }

    /**
     * 用例文件读不出来必须直接失败，不能当成空内容继续判（否则会误判成 AC）
     */
    private String readFile(String dir, String fileName) {
        try {
            return Files.readString(Path.of(dir + fileName));
        } catch (IOException e) {
            throw new JudgeException("测试用例文件读取失败: " + dir + fileName, e);
        }
    }
}
