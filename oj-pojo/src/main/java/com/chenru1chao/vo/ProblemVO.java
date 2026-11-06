package com.chenru1chao.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProblemVO {
    private Integer id;
    private String title;
    private Integer difficulty;
    private Integer submitTotal;
    private Integer passTotal;
    private List<String> tags;
}
