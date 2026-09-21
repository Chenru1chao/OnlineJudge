package com.chenru1chao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chenru1chao.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {

}
