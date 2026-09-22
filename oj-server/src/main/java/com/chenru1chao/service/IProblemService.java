package com.chenru1chao.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.chenru1chao.entity.Problem;
import com.chenru1chao.result.Result;
import com.chenru1chao.vo.ProblemDetailVO;

public interface IProblemService extends IService<Problem> {

    Result<ProblemDetailVO> getProblemDetail(Integer id);
}
