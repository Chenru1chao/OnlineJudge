package com.chenru1chao.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ProblemVO {
    private Integer problemId;
    private String title;
    private Integer difficulty;
    private Integer submitTotal;
    private Integer passTotal;
}
