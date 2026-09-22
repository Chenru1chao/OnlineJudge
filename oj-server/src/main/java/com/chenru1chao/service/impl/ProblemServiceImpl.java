package com.chenru1chao.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.chenru1chao.entity.Problem;
import com.chenru1chao.entity.ProblemSample;
import com.chenru1chao.entity.ProblemTag;
import com.chenru1chao.mapper.ProblemMapper;
import com.chenru1chao.result.Result;
import com.chenru1chao.service.IProblemSampleService;
import com.chenru1chao.service.IProblemService;
import com.chenru1chao.service.IProblemTagService;
import com.chenru1chao.vo.ProblemDetailVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProblemServiceImpl extends ServiceImpl<ProblemMapper, Problem> implements IProblemService {

    private final IProblemSampleService iProblemSampleService;
    private final IProblemTagService iProblemTagService;

    @Override
    public Result<ProblemDetailVO> getProblemDetail(Integer id) {
        Problem problem = getById(id);
        ProblemDetailVO problemDetailVO = BeanUtil.copyProperties(problem, ProblemDetailVO.class);

        List<ProblemSample> problemSamples = iProblemSampleService.lambdaQuery().eq(ProblemSample::getProblemId, id).list();
        problemDetailVO.setProblemSamples(problemSamples);

        List<ProblemTag> problemTags = iProblemTagService.lambdaQuery().eq(ProblemTag::getProblemId, id).list();
        problemDetailVO.setProblemTags(problemTags);

        return Result.success(problemDetailVO);
    }
}
