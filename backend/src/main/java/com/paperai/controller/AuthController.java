package com.paperai.controller;

import com.paperai.model.dto.LoginRequest;
import com.paperai.model.dto.RegisterRequest;
import com.paperai.model.vo.ApiResultVO;
import com.paperai.model.vo.LoginVO;
import com.paperai.service.UserService;
import com.paperai.utils.JwtUtil;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Resource private UserService userService;
    @Resource private JwtUtil jwtUtil;
    @Autowired(required = false)
    private RedissonClient redisson;

    @PostMapping("/register")
    public ApiResultVO<LoginVO> register(@Valid @RequestBody RegisterRequest req) {
        return ApiResultVO.success("注册成功", userService.register(req));
    }

    @PostMapping("/login")
    public ApiResultVO<LoginVO> login(@Valid @RequestBody LoginRequest req) {
        return ApiResultVO.success("登录成功", userService.login(req));
    }

    @PostMapping("/logout")
    public ApiResultVO<String> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            String tokenHash = DigestUtils.md5DigestAsHex(token.getBytes());
            long remainingMs = jwtUtil.getRemainingTtl(token);
            if (remainingMs > 0) {
                RBucket<String> bucket = redisson.getBucket("paperai:jwt:blacklist:" + tokenHash);
                bucket.set("1", remainingMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            }
        }
        return ApiResultVO.success("已退出");
    }
}
