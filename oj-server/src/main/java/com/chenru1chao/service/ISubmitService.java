package com.chenru1chao.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.chenru1chao.dto.SubmitDTO;
import com.chenru1chao.entity.Submit;
import com.chenru1chao.result.Result;
import com.chenru1chao.vo.SubmitResultVO;

public interface ISubmitService extends IService<Submit> {
    Result<SubmitResultVO> handleUserSubmit(SubmitDTO submitDTO);
}
