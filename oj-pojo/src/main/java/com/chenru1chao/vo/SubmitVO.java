package com.chenru1chao.vo;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SubmitVO {
    private Integer id;

    private Integer userId;

    private Integer problemId;

    private Integer status;

    private Integer failedCaseNo;

    private String errorMsg;

    private String submitLanguage;
    // ms
    private Integer timeUsed;
    // kb
    private Integer memoryUsed;

    private LocalDateTime submitTime;
}
