package com.chenru1chao.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.chenru1chao.dto.UserLoginDTO;
import com.chenru1chao.entity.User;
import com.chenru1chao.result.Result;

public interface IUserService extends IService<User> {


    Result login(UserLoginDTO userLoginDTO);
}
