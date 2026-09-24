package com.chenru1chao.util;

import cn.hutool.core.lang.UUID;
import com.aliyun.oss.OSS;
import com.chenru1chao.config.AliyunProperties;
import com.chenru1chao.exception.AliyunOssException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AliyunOssUtil {
    private final OSS ossClient;
    private final AliyunProperties aliyunProperties;

    public String loadAvatar(byte[] avatar, String originalFilename) {
        if (avatar == null || avatar.length == 0)
            throw new AliyunOssException("文件不能为空");

        long maxSize = aliyunProperties.getMaxSizeMb() * 1024 * 1024;
        if (avatar.length > maxSize)
            throw new AliyunOssException("文件体积过大 最大为2MB");

        if (originalFilename == null || !originalFilename.contains(".")) {
            throw new AliyunOssException("文件缺少扩展名");
        }

        String ext = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();

        String allowedExtensions = aliyunProperties.getAllowedExtensions();
        List<String> legalExtensions = Arrays.asList(allowedExtensions.split(","));

        if (!legalExtensions.contains(ext))
            throw new AliyunOssException("只允许上传：" + allowedExtensions);

        String objectName = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        ossClient.putObject(aliyunProperties.getBucketName(), objectName, new ByteArrayInputStream(avatar));

        //https://BucketName.Endpoint/ObjectName
        return new StringBuilder("https://")
                .append(aliyunProperties.getBucketName())
                .append(".")
                .append(aliyunProperties.getEndpoint())
                .append("/")
                .append(objectName).toString();
    }
}
