package com.chenru1chao.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.chenru1chao.dto.SubmitDTO;
import com.chenru1chao.dto.SubmitPageDTO;
import com.chenru1chao.entity.Submit;
import com.chenru1chao.result.Result;
import com.chenru1chao.vo.HeatmapCellVO;
import com.chenru1chao.vo.PageResult;
import com.chenru1chao.vo.SubmitDetailVO;
import com.chenru1chao.vo.SubmitPageVO;

import java.util.List;

public interface ISubmitService extends IService<Submit> {
    Result<SubmitDetailVO> handleUserSubmit(SubmitDTO submitDTO);

    Result<SubmitDetailVO> getSubmitStatus(Integer id);

    Result<PageResult<SubmitPageVO>> getSubmitPage(SubmitPageDTO submitPageDTO);

    /** 提交墙：最近 days 天里每天的提交次数。userId 为空时取当前登录用户。 */
    Result<List<HeatmapCellVO>> getHeatmap(Integer userId, Integer days);
}
