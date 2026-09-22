package com.chenru1chao.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.chenru1chao.dto.UserLoginDTO;
import com.chenru1chao.entity.User;
import com.chenru1chao.mapper.UserMapper;
import com.chenru1chao.result.Result;
import com.chenru1chao.service.IUserService;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Override
    public Result login(UserLoginDTO userLoginDTO) {
        boolean exists = lambdaQuery().eq(User::getUsername, userLoginDTO.getUsername())
                .eq(User::getPassword, userLoginDTO.getPassword()).exists();
        if (!exists) {
            return Result.error("账号或者是密码错误");
        }
        return Result.success();
    }
}
