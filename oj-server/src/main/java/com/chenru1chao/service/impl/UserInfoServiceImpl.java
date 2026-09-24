package com.chenru1chao.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.chenru1chao.entity.UserInfo;
import com.chenru1chao.mapper.UserInfoMapper;
import com.chenru1chao.service.IUserInfoService;
import org.springframework.stereotype.Service;

@Service
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements IUserInfoService {
}
