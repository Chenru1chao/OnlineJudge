package com.chenru1chao.controller;

import com.chenru1chao.dto.UserLoginDTO;
import com.chenru1chao.result.Result;
import com.chenru1chao.service.IUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final IUserService iUserService;

    @PostMapping("/login")
    public Result login(@RequestBody UserLoginDTO userLoginDTO) {
        return iUserService.login(userLoginDTO);
    }

    @PostMapping("/register")
    public void register() {

    }

    @PostMapping("/logout")
    public void logout() {

    }

    @GetMapping("/me")
    public void me() {

    }
}
