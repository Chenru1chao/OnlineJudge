package com.chenru1chao.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RankVO {
    private Integer id;
    private String username;
    private Integer totalAccept;
    private Integer totalSubmit;
}
