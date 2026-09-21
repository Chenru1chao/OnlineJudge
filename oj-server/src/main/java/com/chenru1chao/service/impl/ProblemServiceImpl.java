package com.chenru1chao.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.chenru1chao.entity.Problem;
import com.chenru1chao.mapper.ProblemMapper;
import com.chenru1chao.service.IProblemService;
import org.springframework.stereotype.Service;

@Service
public class ProblemServiceImpl extends ServiceImpl<ProblemMapper, Problem> implements IProblemService {

}
