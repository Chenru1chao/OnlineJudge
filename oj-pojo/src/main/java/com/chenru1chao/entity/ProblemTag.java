package com.chenru1chao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName(value = "problem_tag")
public class ProblemTag {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    private Integer tagId;

    private Integer problemId;
}