package com.chenru1chao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName(value = "submit")
public class Submit {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    private Integer userId;

    private String username;

    private Integer problemId;

    private Integer status;

    private LocalDateTime submitTime;

    private String submitLanguage;
    /** 运行时间(ms) */
    private Integer timeUsed;
    /** 运行内存(kb) */
    private Integer memoryUsed;

    private String code;
}