package com.chenru1chao.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties("oj.jwt")
public class JwtProperties {
    private String key;
    private Long ttl;
    private String tokenName;
}
