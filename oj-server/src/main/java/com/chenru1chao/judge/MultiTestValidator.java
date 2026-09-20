package com.chenru1chao.judge;

import java.util.Arrays;
import java.util.List;

public class MultiTestValidator {

    // TODO: 这里采用宽松的答案校验 后续需要逐行比对 需改进!!!
    private static List<String> tokenize(String text) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return List.of();
        }
        return Arrays.asList(trimmed.split("\\s+"));
    }

    public static boolean answerValidator(String userAnswer, String standardAnswer) {
        return MultiTestValidator.tokenize(standardAnswer).equals(MultiTestValidator.tokenize(userAnswer));
    }
}
