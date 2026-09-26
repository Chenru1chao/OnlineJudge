package com.chenru1chao.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class SubmitDTO implements Serializable {
    private Integer problemId;

    private String submitLanguage;

    private String code;
}
