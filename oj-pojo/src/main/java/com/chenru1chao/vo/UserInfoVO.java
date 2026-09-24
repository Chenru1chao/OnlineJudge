package com.chenru1chao.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class UserInfoVO {
    private String realName;

    private String phone;

    private String github;

    private String school;

    private String major;

    private LocalDateTime createTime;
}
