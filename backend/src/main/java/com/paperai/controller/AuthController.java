package com.paperai.controller;

import com.paperai.model.dto.LoginRequest;
import com.paperai.model.dto.RegisterRequest;
import com.paperai.model.vo.ApiResultVO;
import com.paperai.model.vo.LoginVO;
import com.paperai.service.UserService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Resource private UserService userService;

    @PostMapping("/register")
    public ApiResultVO<LoginVO> register(@Valid @RequestBody RegisterRequest req) {
        return ApiResultVO.success("注册成功", userService.register(req));
    }

    @PostMapping("/login")
    public ApiResultVO<LoginVO> login(@Valid @RequestBody LoginRequest req) {
        return ApiResultVO.success("登录成功", userService.login(req));
    }

    @PostMapping("/logout")
    public ApiResultVO<String> logout() {
        return ApiResultVO.success("已退出");
    }
}
