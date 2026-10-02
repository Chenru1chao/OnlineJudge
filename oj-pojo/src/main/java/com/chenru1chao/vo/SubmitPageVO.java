package com.chenru1chao.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SubmitPageVO {
    private Integer id;
    private Integer userId;
    private String username;
    private Integer problemId;
    private String title;
    private Integer status;
    private Integer timeUsed;
    private Integer memoryUsed;
    private String submitLanguage;
    private LocalDateTime submitTime;
}
