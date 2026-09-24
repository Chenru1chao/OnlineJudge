package com.chenru1chao.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 题目提示用例（对外）
 * 这里的 input / output 是文件内容，不是文件名
 */
@Data
@AllArgsConstructor
public class ProblemSampleVO {
    private Integer sort;

    private String input;

    private String output;
}
