package com.chenru1chao.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SubmitDetailVO {
    private Integer id;

    private Integer userId;

    private Integer problemId;

    private Integer status;

    private Integer failedCaseNo;

    private String errorMsg;

    private String submitLanguage;

    private String code;
    // ms
    private Integer timeUsed;
    // kb
    private Integer memoryUsed;

    private LocalDateTime submitTime;
}
