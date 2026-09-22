package com.chenru1chao.judge;

import com.chenru1chao.config.TestCaseConfig;
import com.chenru1chao.entity.ProblemTestCase;
import com.chenru1chao.exception.SandboxException;
import com.chenru1chao.judge.model.TestCase;
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
public class SandboxTestCaseLoader {

    private final TestCaseConfig testCaseConfig;
    private final IProblemTestCaseService iProblemTestCaseService;

    public List<TestCase> testCaseLoader(Integer problemId) {
        List<ProblemTestCase> testCaseFiles = iProblemTestCaseService.query().eq("problem_id", problemId).list();

        ArrayList<TestCase> testCases = new ArrayList<>();

        String path = testCaseConfig.getPrefix();
        String prefix = path.replace("problemId", problemId.toString());

        for (ProblemTestCase testCase : testCaseFiles) {
            try {
                String input = new String(Files.readAllBytes(Path.of(prefix + testCase.getInput())));
                String output = new String(Files.readAllBytes(Path.of(prefix + testCase.getOutput())));
                testCases.add(new TestCase(input, output));
            } catch (IOException e) {
                throw new SandboxException("测试用例文件读取失败: " + prefix + testCase.getInput(), e);
            }
        }

        return testCases;
    }
}
