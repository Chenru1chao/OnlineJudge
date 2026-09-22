package com.chenru1chao.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.chenru1chao.entity.ProblemTag;
import com.chenru1chao.mapper.ProblemTagMapper;
import com.chenru1chao.service.IProblemTagService;
import org.springframework.stereotype.Service;

@Service
public class ProblemTagServiceImpl extends ServiceImpl<ProblemTagMapper, ProblemTag> implements IProblemTagService {

}
