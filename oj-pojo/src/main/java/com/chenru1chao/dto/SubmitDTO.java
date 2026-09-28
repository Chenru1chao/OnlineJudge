package com.chenru1chao.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

@Data
public class SubmitDTO implements Serializable {

    @NotNull(message = "题目不能为空")
    private Integer problemId;

    private String submitLanguage;

    @NotBlank(message = "提交的代码不能为空")
    @Size(max = 20000, message = "代码长度过长")
    private String code;
}
