package com.chenru1chao.judge;

import java.util.Arrays;
import java.util.List;

public class MultiTestValidator {
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
