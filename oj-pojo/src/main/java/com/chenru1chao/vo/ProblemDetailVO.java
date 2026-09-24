package com.chenru1chao.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
@AllArgsConstructor
public class ProblemDetailVO implements Serializable {
    private Integer id;

    private String title;

    private String input;

    private String output;

    private String description;

    private Integer difficulty;

    private Integer timeLimit;

    private Integer memoryLimit;

    private Integer submitTotal;

    private Integer passTotal;

    private String author;

    private List<ProblemSampleVO> problemSamples;

    private List<ProblemTagVO> problemTags;
}
