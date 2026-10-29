package com.chenru1chao.judge;

import java.util.Arrays;
import java.util.List;

public class JudgeAnswerChecker {
    private static List<String> tokenize(String text) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return List.of();
        }
        return Arrays.asList(trimmed.split("\\s+"));
    }

    public static boolean isAnswerCorrect(String userAnswer, String standardAnswer) {
        return JudgeAnswerChecker.tokenize(standardAnswer).equals(JudgeAnswerChecker.tokenize(userAnswer));
    }
}
