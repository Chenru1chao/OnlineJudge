package com.chenru1chao.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.chenru1chao.config.JwtProperties;
import com.chenru1chao.dto.UserDTO;
import com.chenru1chao.dto.UserInfoDTO;
import com.chenru1chao.dto.UserLoginDTO;
import com.chenru1chao.dto.UserRegisterDTO;
import com.chenru1chao.entity.User;
import com.chenru1chao.entity.UserAccept;
import com.chenru1chao.entity.UserInfo;
import com.chenru1chao.mapper.UserMapper;
import com.chenru1chao.result.Result;
import com.chenru1chao.service.IUserAcceptService;
import com.chenru1chao.service.IUserInfoService;
import com.chenru1chao.service.IUserService;
import com.chenru1chao.util.AliyunOssUtil;
import com.chenru1chao.util.JwtUtil;
import com.chenru1chao.util.UserContext;
import com.chenru1chao.vo.LoginVO;
import com.chenru1chao.vo.UserInfoVO;
import com.chenru1chao.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;

@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    private final JwtProperties jwtProperties;
    private final IUserInfoService iUserInfoService;
    private final AliyunOssUtil aliyunOssUtil;
    private final IUserAcceptService iUserAcceptService;

    @Override
    public Result<LoginVO> login(UserLoginDTO userLoginDTO) {
        // TODO: 去user表 根据username建个索引
        User user = lambdaQuery().eq(User::getUsername, userLoginDTO.getUsername()).one();

        if (user == null || !BCrypt.checkpw(userLoginDTO.getPassword(), user.getPassword())) {
            return Result.error("账号或者是密码错误");
        }

        HashMap<String, Object> payload = new HashMap<>();
        payload.put("UserId", user.getId());

        LoginVO loginVO = BeanUtil.copyProperties(user, LoginVO.class);

        loginVO.setToken(JwtUtil.createJwt(jwtProperties.getKey(), jwtProperties.getTtl(), payload));

        return Result.success(loginVO);
    }

    @Override
    @Transactional
    public Result<Void> register(UserRegisterDTO userRegisterDTO) {
        User user = BeanUtil.copyProperties(userRegisterDTO, User.class);

        // 用户的密码不保存铭文到数据库 加密后落库
        user.setPassword(BCrypt.hashpw(userRegisterDTO.getPassword(), BCrypt.gensalt()));

        save(user);

        UserInfo userInfo = new UserInfo();

        userInfo.setUserId(user.getId());

        iUserInfoService.save(userInfo);

        return Result.success();
    }

    @Override
    public Result<UserVO> getUser() {
        Integer userId = UserContext.get();

        User user = getById(userId);

        UserVO userVO = BeanUtil.copyProperties(user, UserVO.class);

        return Result.success(userVO);
    }

    @Override
    public Result<UserInfoVO> getUserInfo() {
        Integer userId = UserContext.get();

        UserInfo userInfo = iUserInfoService.getById(userId);

        UserInfoVO userInfoVO = BeanUtil.copyProperties(userInfo, UserInfoVO.class);

        return Result.success(userInfoVO);
    }

    @Override
    @Transactional
    public Result<Void> updateUserInfo(UserInfoDTO userInfoDTO) {
        Integer userId = UserContext.get();

        // realName/phone/GitHub/school/major 是 user_info 表的
        UserInfo userInfo = BeanUtil.copyProperties(userInfoDTO, UserInfo.class);
        userInfo.setUserId(userId);

        if (userInfo.getRealName() != null
                || userInfo.getPhone() != null
                || userInfo.getGithub() != null
                || userInfo.getSchool() != null
                || userInfo.getMajor() != null) {
            iUserInfoService.saveOrUpdate(userInfo);
        }

        return Result.success();
    }

    @Override
    public Result<Void> updateUser(UserDTO userDTO) {
        Integer userId = UserContext.get();

        User user = BeanUtil.copyProperties(userDTO, User.class);
        user.setId(userId);

        if (userDTO.getAge() != null || userDTO.getGender() != null || userDTO.getMood() != null)
            updateById(user);

        return Result.success();
    }

    @Override
    public Result<Void> loadAvatar(MultipartFile multipartFile) throws IOException {
        byte[] avatar = multipartFile.getBytes();
        String originalFilename = multipartFile.getOriginalFilename();

        String url = aliyunOssUtil.loadAvatar(avatar, originalFilename);

        User user = new User();
        user.setId(UserContext.get());
        user.setAvatar(url);

        updateById(user);

        return Result.success();
    }

    // TODO: 用redis做缓存如果可以redis缓存命中直接返回 未命中查数据库构建缓存
    @Override
    public Result<Long> getUserTotalAccept() {
        Integer userId = UserContext.get();
        Long total = iUserAcceptService.lambdaQuery()
                .eq(UserAccept::getUserId, userId)
                .count();

        // TODO: 构建缓存到redis中

        return Result.success(total);
    }
}
