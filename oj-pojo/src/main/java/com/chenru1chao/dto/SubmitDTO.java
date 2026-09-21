package com.chenru1chao.dto;

import lombok.Data;

@Data
public class SubmitDTO {

    private Integer userId;

    private Integer problemId;

    private String language;

    private String code;
}
