package com.chenru1chao.controller;

import com.chenru1chao.dto.UserDTO;
import com.chenru1chao.dto.UserInfoDTO;
import com.chenru1chao.dto.UserLoginDTO;
import com.chenru1chao.dto.UserRegisterDTO;
import com.chenru1chao.result.Result;
import com.chenru1chao.service.IUserService;
import com.chenru1chao.util.UserContext;
import com.chenru1chao.vo.LoginVO;
import com.chenru1chao.vo.UserInfoVO;
import com.chenru1chao.vo.UserProfileVO;
import com.chenru1chao.vo.UserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final IUserService iUserService;

    @PostMapping("/login")
    public Result<LoginVO> login(@RequestBody UserLoginDTO userLoginDTO) {
        return iUserService.login(userLoginDTO);
    }

    @PostMapping("/register")
    public Result<Void> register(@RequestBody @Valid UserRegisterDTO userRegisterDTO) {
        return iUserService.register(userRegisterDTO);
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        return Result.success();
    }

    @GetMapping
    public Result<UserVO> getUser() {
        return iUserService.getUser();
    }

    @PutMapping
    public Result<Void> updateUser(@RequestBody UserDTO userDTO) {
        return iUserService.updateUser(userDTO);
    }

    @GetMapping("/me")
    public Result<UserInfoVO> getUserInfo() {
        return iUserService.getUserInfo();
    }

    @PutMapping("/me")
    public Result<Void> updateUserInfo(@RequestBody @Valid UserInfoDTO userInfoDTO) {
        return iUserService.updateUserInfo(userInfoDTO);
    }

    @GetMapping("/me/accept")
    public Result<Long> getUserTotalAccept() {
        return iUserService.getUserTotalAccept();
    }

    @PostMapping("/me/load")
    public Result<Void> loadAvatar(MultipartFile file) throws IOException {
        return iUserService.loadAvatar(file);
    }

    @GetMapping("/accept/problem")
    public Result<List<Integer>> getUserAcceptProblemList() {
        return iUserService.getUserAcceptProblemList(UserContext.get());
    }

    @GetMapping("/{id}/profile")
    public Result<UserProfileVO> getUserProfile(@PathVariable Integer id) {
        return iUserService.getUserProfile(id);
    }

    @GetMapping("/{id}/accept/problem")
    public Result<List<Integer>> getUserProfileAcceptProblemList(@PathVariable Integer id) {
        return iUserService.getUserAcceptProblemList(id);
    }
}
