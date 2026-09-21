package com.chenru1chao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 题目提示用例表
 */
@Data
@TableName(value = "problem_sample")
public class ProblemSample {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    private Integer problemId;

    private String input;

    private String output;

    // 排序字段
    private Integer sort;
}