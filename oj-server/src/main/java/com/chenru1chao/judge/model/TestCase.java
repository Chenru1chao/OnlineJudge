package com.chenru1chao.judge.model;

import lombok.Data;

@Data
public class TestCase {
    private String stdin;
    private String stdout;

    public TestCase(String stdin, String stdout) {
        this.stdin = (stdin == null) ? "" : stdin;
        this.stdout = stdout;
    }
}
