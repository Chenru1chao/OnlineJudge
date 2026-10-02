package com.chenru1chao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chenru1chao.entity.User;
import com.chenru1chao.vo.RankVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    List<RankVO> getUserRank(@Param("begin") Integer begin, @Param("pageSize") Integer pageSize);

    Integer getTotalUser();
}
