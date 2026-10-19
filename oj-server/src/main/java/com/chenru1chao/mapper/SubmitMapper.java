package com.chenru1chao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chenru1chao.entity.Submit;
import com.chenru1chao.vo.HeatmapCellVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SubmitMapper extends BaseMapper<Submit> {
    List<Integer> selectActiveUserIds(@Param("since") LocalDateTime since);

    Integer reconcileTotalSubmit(List<Integer> userIds);

    /** 提交墙：按天分组统计某个用户在 since 之后的提交次数。 */
    List<HeatmapCellVO> selectDailySubmitCounts(@Param("userId") Integer userId,
                                                @Param("since") LocalDateTime since);
}
