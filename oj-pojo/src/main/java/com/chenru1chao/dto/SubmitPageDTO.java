package com.chenru1chao.dto;

import lombok.Data;

@Data
public class SubmitPageDTO {
    private Integer pageNO = 1;
    private Integer pageSize = 10;
    private Integer problemId;
    private Integer userId;
}
