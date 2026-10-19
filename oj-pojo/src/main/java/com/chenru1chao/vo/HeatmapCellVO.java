package com.chenru1chao.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 提交墙的一格：某一天提交了多少次。
 * statDate 用 String（YYYY-MM-DD）而不是 LocalDate，前端拿到就能直接当 map 的 key。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class HeatmapCellVO {
    private String statDate;

    private Integer count;
}
