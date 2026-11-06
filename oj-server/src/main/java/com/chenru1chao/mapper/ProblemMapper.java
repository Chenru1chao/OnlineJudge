package com.chenru1chao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chenru1chao.dto.ProblemPageDTO;
import com.chenru1chao.entity.Problem;
import com.chenru1chao.vo.ProblemTagVO;
import com.chenru1chao.vo.ProblemVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProblemMapper extends BaseMapper<Problem> {

    List<ProblemTagVO> queryProblemTags(@Param("problemId") Integer problemId);

    Page<ProblemVO> queryProblemPage(Page<ProblemVO> page, @Param("dto") ProblemPageDTO dto);

    List<ProblemTagVO> queryTagsByProblemIds(@Param("ids") List<Integer> ids);
}
