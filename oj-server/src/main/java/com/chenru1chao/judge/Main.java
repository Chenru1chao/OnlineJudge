package com.chenru1chao.judge;

import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        System.out.println(tokenize("1\r\n2\n"));
    }

    private static List<String> tokenize(String text) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return List.of();
        }
        return Arrays.asList(trimmed.split("\\s+"));
    }
}
