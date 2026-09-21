package com.chenru1chao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName(value = "user_info")
public class UserInfo {

    @TableId(value = "user_id", type = IdType.AUTO)
    private Integer userId;

    private String realName;

    private String phone;

    private String github;

    private String email;

    private String school;

    private String major;

    private LocalDateTime createTime;
}
