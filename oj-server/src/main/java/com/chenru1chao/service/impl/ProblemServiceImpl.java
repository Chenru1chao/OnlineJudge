package com.chenru1chao.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.chenru1chao.dto.PageDTO;
import com.chenru1chao.entity.Problem;
import com.chenru1chao.entity.ProblemSample;
import com.chenru1chao.exception.ProblemDataException;
import com.chenru1chao.exception.ProblemNotFoundException;
import com.chenru1chao.judge.ProblemFileReader;
import com.chenru1chao.mapper.ProblemMapper;
import com.chenru1chao.result.Result;
import com.chenru1chao.service.IProblemSampleService;
import com.chenru1chao.service.IProblemService;
import com.chenru1chao.vo.PageResult;
import com.chenru1chao.vo.ProblemDetailVO;
import com.chenru1chao.vo.ProblemSampleVO;
import com.chenru1chao.vo.ProblemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static com.chenru1chao.constant.PageConstant.*;

@Service
@RequiredArgsConstructor
public class ProblemServiceImpl extends ServiceImpl<ProblemMapper, Problem> implements IProblemService {

    private final IProblemSampleService iProblemSampleService;
    private final ProblemFileReader problemFileReader;
    private final ProblemMapper problemMapper;

    @Override
    public Result<ProblemDetailVO> getProblemDetail(Integer id) {
        Problem problem = getById(id);

        if (problem == null) {
            throw new ProblemNotFoundException("该题目不存在!");
        }

        ProblemDetailVO problemDetailVO = BeanUtil.copyProperties(problem, ProblemDetailVO.class);

        problemDetailVO.setProblemSamples(loadSamples(id));
        problemDetailVO.setProblemTags(problemMapper.queryProblemTags(id));

        return Result.success(problemDetailVO);
    }

    private List<ProblemSampleVO> loadSamples(Integer problemId) {
        List<ProblemSample> sampleFiles = iProblemSampleService.lambdaQuery()
                .eq(ProblemSample::getProblemId, problemId)
                .orderByAsc(ProblemSample::getSort)
                .list();

        String dir = problemFileReader.resolveDir(problemId);
        List<ProblemSampleVO> samples = new ArrayList<>(sampleFiles.size());

        for (ProblemSample sampleFile : sampleFiles) {
            String input = problemFileReader.readIfExists(dir, sampleFile.getInputFile());
            String output = problemFileReader.readIfExists(dir, sampleFile.getOutputFile());

            // 样例是展示用的，读不到就说明数据没配好。报错比默默显示空白强
            if (input == null) {
                throw new ProblemDataException("样例输入文件读不到: " + dir + sampleFile.getInputFile());
            }
            if (output == null) {
                throw new ProblemDataException("样例输出文件读不到: " + dir + sampleFile.getOutputFile());
            }

            samples.add(new ProblemSampleVO(sampleFile.getSort(), input, output));
        }

        return samples;
    }

    @Override
    public Result<PageResult> getProblemPage(PageDTO pageDTO) {
        long pageNO = pageDTO.getPageNO() == null || pageDTO.getPageNO() < 1
                ? DEFAULT_PAGE_NO : pageDTO.getPageNO();
        long pageSize = pageDTO.getPageSize() == null
                ? DEFAULT_PAGE_SIZE : Math.min(Math.max(pageDTO.getPageSize(), 1), MAX_PAGE_SIZE);

        Page<Problem> page = new Page<>(pageNO, pageSize);
        lambdaQuery()
                .eq(pageDTO.getDifficulty() != null, Problem::getDifficulty, pageDTO.getDifficulty())
                .like(pageDTO.getTitle() != null && !pageDTO.getTitle().isBlank(), Problem::getTitle, pageDTO.getTitle())
                .orderByAsc(Problem::getId)
                .page(page);

        List<ProblemVO> problemVOS = new ArrayList<>(page.getRecords().size());
        for (Problem problem : page.getRecords()) {
            problemVOS.add(BeanUtil.copyProperties(problem, ProblemVO.class));
        }

        PageResult pageResult = new PageResult((int) page.getTotal(), (int) pageNO, (int) pageSize, problemVOS);
        return Result.success(pageResult);
    }
}
