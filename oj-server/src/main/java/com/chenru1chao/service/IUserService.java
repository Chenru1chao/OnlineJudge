package com.chenru1chao.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.chenru1chao.dto.UserDTO;
import com.chenru1chao.dto.UserInfoDTO;
import com.chenru1chao.dto.UserLoginDTO;
import com.chenru1chao.dto.UserRegisterDTO;
import com.chenru1chao.entity.User;
import com.chenru1chao.result.Result;
import com.chenru1chao.vo.UserInfoVO;
import com.chenru1chao.vo.LoginVO;
import com.chenru1chao.vo.UserVO;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface IUserService extends IService<User> {


    Result<LoginVO> login(UserLoginDTO userLoginDTO);

    Result<Void> register(UserRegisterDTO userRegisterDTO);

    Result<UserVO> getUser();

    Result<UserInfoVO> getUserInfo();

    Result<Void> updateUserInfo(UserInfoDTO userInfoDTO);

    Result<Void> loadAvatar(MultipartFile multipartFile) throws IOException;

    Result<Void> updateUser(UserDTO userDTO);
}
