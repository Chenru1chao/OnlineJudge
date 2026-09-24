package com.chenru1chao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 题目测试用例表
 */
@Data
@TableName(value = "problem_test_case")
public class ProblemTestCase {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    private Integer problemId;

    // 存的是文件名，不是文件内容
    private String inputFile;

    // 存的是文件名，不是文件内容
    private String outputFile;

    private Integer sort;
}