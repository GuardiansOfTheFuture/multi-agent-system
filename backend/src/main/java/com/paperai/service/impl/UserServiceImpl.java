package com.paperai.service.impl;

import com.paperai.common.BusinessException;
import com.paperai.common.ResultCode;
import com.paperai.mapper.UserMapper;
import com.paperai.model.dto.LoginRequest;
import com.paperai.model.dto.RegisterRequest;
import com.paperai.model.entity.User;
import com.paperai.model.vo.LoginVO;
import com.paperai.model.vo.UserVO;
import com.paperai.service.UserService;
import com.paperai.utils.JwtUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class UserServiceImpl implements UserService {

    @Resource private UserMapper userMapper;
    @Resource private PasswordEncoder passwordEncoder;
    @Resource private JwtUtil jwtUtil;

    @Override
    public LoginVO register(RegisterRequest req) {
        if (userMapper.findByUsername(req.getUsername()) != null) {
            throw new BusinessException(ResultCode.CONFLICT, "用户名已存在");
        }
        User user = new User();
        user.setUsername(req.getUsername());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setEmail(req.getEmail());
        userMapper.insert(user);
        log.info("用户注册: id={}, username={}", user.getId(), user.getUsername());
        return toLoginVO(user);
    }

    @Override
    public LoginVO login(LoginRequest req) {
        User user = userMapper.findByUsername(req.getUsername());
        if (user == null) throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "密码错误");
        }
        log.info("用户登录: id={}, username={}", user.getId(), user.getUsername());
        return toLoginVO(user);
    }

    @Override
    @Cacheable(value = "users", key = "#id")
    public UserVO getUserById(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        return toUserVO(user);
    }

    private LoginVO toLoginVO(User user) {
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        return new LoginVO(token, toUserVO(user));
    }

    private UserVO toUserVO(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setEmail(user.getEmail());
        vo.setAvatar(user.getAvatar());
        vo.setCreatedAt(user.getCreatedAt());
        return vo;
    }
}
