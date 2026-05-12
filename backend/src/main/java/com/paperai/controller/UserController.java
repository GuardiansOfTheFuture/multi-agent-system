package com.paperai.controller;


import com.paperai.service.UserService;
import jakarta.annotation.Resource;
import org.springframework.security.core.Authentication;
import com.paperai.model.vo.ApiResultVO;
import com.paperai.model.vo.UserVO;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
public class UserController {

    @Resource private UserService userService;

    @GetMapping("/me")
    public ApiResultVO<UserVO> me(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ApiResultVO.success(userService.getUserById(userId));
    }
}
