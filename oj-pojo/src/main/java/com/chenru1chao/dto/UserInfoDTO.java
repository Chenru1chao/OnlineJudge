package com.chenru1chao.dto;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UserInfoDTO {
    private String realName;

    @Pattern(regexp = "^[0-9]{11}$|^$",
            message = "手机格式非法")
    private String phone;

    private String github;

    private String school;

    private String major;
}
