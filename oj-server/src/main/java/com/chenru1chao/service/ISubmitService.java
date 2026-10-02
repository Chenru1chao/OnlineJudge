package com.chenru1chao.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.chenru1chao.dto.SubmitDTO;
import com.chenru1chao.dto.SubmitPageDTO;
import com.chenru1chao.entity.Submit;
import com.chenru1chao.result.Result;
import com.chenru1chao.vo.PageResult;
import com.chenru1chao.vo.SubmitDetailVO;
import com.chenru1chao.vo.SubmitPageVO;

public interface ISubmitService extends IService<Submit> {
    Result<SubmitDetailVO> handleUserSubmit(SubmitDTO submitDTO);

    Result<SubmitDetailVO> getSubmitStatus(Integer id);

    Result<PageResult<SubmitPageVO>> getSubmitPage(SubmitPageDTO submitPageDTO);
}
