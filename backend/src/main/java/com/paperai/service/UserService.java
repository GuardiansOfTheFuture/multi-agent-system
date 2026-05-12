package com.paperai.service;

import com.paperai.model.dto.LoginRequest;
import com.paperai.model.dto.RegisterRequest;
import com.paperai.model.vo.LoginVO;
import com.paperai.model.vo.UserVO;

public interface UserService {
    LoginVO register(RegisterRequest req);
    LoginVO login(LoginRequest req);
    UserVO getUserById(Long id);
}
