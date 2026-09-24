package com.chenru1chao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName(value = "user")
public class User {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    private String avatar;

    private String username;

    private String email;

    private String password;

    private Integer age;

    private String gender;

    private String mood;

    private Integer totalSubmit;

    private Integer totalAccept;
}
