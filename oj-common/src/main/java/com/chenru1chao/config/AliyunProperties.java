package com.chenru1chao.config;


import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties("oj.alioss")
public class AliyunProperties {
    private String accessKeyId;
    private String accessKeySecret;
    private String bucketName;
    private String endpoint;
    private Integer maxSizeMb;
    private String allowedExtensions;

    @Bean(destroyMethod = "shutdown")
    public OSS ossClient() {
        return new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
    }
}
