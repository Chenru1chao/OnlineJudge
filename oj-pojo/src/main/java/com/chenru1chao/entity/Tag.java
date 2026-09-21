package com.chenru1chao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@TableName(value = "tag")
@Data
public class Tag {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    private String tagInfo;
}