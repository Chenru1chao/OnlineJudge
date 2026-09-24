package com.chenru1chao.dto;

import lombok.Data;

@Data
public class PageDTO {
    private Integer pageNO = 1;
    private Integer pageSize = 10;
    private Integer difficulty;
    private String title;
    private String tag;
}
