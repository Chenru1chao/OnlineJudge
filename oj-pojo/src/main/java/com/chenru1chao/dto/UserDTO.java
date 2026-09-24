package com.chenru1chao.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserDTO {
    @Min(value = 1, message = "年龄必须在 1~199 之间")
    @Max(value = 199, message = "年龄必须在 1~199 之间")
    private Integer age;

    @Pattern(regexp = "^[男女]$", message = "性别只能是男或女")
    private String gender;

    @Size(max = 100, message = "个性签名最多 100 字")
    private String mood;
}
