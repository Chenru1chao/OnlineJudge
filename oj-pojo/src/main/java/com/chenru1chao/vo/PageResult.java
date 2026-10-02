package com.chenru1chao.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class PageResult <T> {
    Integer total;
    Integer pageNo;
    Integer pageSize;
    List<T> data;
}
