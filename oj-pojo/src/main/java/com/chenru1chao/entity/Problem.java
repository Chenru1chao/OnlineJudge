package com.chenru1chao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName(value = "problem")
public class Problem implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    private String title;

    private String input;

    private String output;

    private String description;

    private Integer difficulty;

    private Integer timeLimit;

    private Integer memoryLimit;

    private Integer submitTotal;

    private Integer passTotal;

    private String author;

    private LocalDateTime createTime;
}