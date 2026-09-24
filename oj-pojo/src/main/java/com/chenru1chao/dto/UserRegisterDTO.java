package com.chenru1chao.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserRegisterDTO {

    @NotBlank(message = "用户名不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9_]{3,}$",
            message = "用户名格式错误")
    private String username;

    @NotBlank(message = "邮箱不能为空")
    @Email(regexp = "^[a-zA-Z0-9-]+@[a-zA-Z0-9]+\\.[a-zA-Z]{2,}$",
            message = "非法的邮箱格式")
    @Size(max = 50, message = "邮箱太长")
    private String email;

    @NotBlank(message = "密码不能为空")
    @Pattern(regexp = "^\\S{6,}$",
            message = "密码长度必须大于等于6")

    private String password;
}
