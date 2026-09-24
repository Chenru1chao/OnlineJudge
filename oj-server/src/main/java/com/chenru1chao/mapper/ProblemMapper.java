package com.chenru1chao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chenru1chao.entity.Problem;
import com.chenru1chao.vo.ProblemTagVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProblemMapper extends BaseMapper<Problem> {

    /**
     * 查某个题目的标签，带上标签文字。
     * problem_tag 里只有 tag_id，直接返回的话前端只能看到数字。
     * <p>
     * 用注解写而不是写进 ProblemMapper.xml，因为项目没配 mybatis-plus.mapper-locations，
     * MP 默认只扫 classpath*:/mapper/ 目录下的 XML，resources/com.chenru1chao.mapper/ 下的不会被加载。
     */
    List<ProblemTagVO> queryProblemTags(@Param("problemId") Integer problemId);
}
