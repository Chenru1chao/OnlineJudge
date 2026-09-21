package com.chenru1chao;

import com.chenru1chao.judge.JavaProcessSandbox;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class ProcessSandboxTest {

    @Autowired
    JavaProcessSandbox javaProcessSandbox;
    @Test
    public void test() {
        javaProcessSandbox.compileAndRun(1, """
                public class Main {
                    public static void main(String[] args) {
                        System.out.println("hello world!");
                    }
                }
                """);
    }
}
