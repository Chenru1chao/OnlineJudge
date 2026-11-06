package com.chenru1chao.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProblemTagVO {

    /** 批量查询题目标签时使用；题目详情接口不返回该字段，保持 null */
    private Integer problemId;

    private Integer tagId;

    private String tagInfo;
}
