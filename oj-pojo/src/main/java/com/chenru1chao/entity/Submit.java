package com.chenru1chao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@TableName(value = "submit")
public class Submit {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    private Integer userId;

    private Integer problemId;

    private Integer status;

    private Integer failedCaseNo;

    private String errorMsg;

    private String submitLanguage;
    /** 运行时间(ms) */
    private Integer timeUsed;
    /** 运行内存(kb) */
    private Integer memoryUsed;

    private String code;

    private LocalDateTime submitTime;
}