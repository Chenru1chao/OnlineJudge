package com.chenru1chao.judge;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.nio.file.Path;

@Component
@Slf4j
public class SandboxCleaner {
    public static void deleteFile(Path workDir) {
        if (workDir != null) {
            try {
                FileSystemUtils.deleteRecursively(workDir);
            } catch (IOException e) {
                // TODO: 这里不能再抛了 会吞异常用日志记录 先用控制台 后续完善保存到日志文件里 人工介入删除
                log.error("{}:文件删除失败", workDir);
            }
        }
    }
}
