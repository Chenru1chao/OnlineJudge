package com.chenru1chao.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "test-case.file-path")
public class TestCaseConfig {
    private String prefix;
}
