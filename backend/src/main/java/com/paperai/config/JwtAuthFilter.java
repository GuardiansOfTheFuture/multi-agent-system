package com.paperai.config;

import com.paperai.utils.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final RedissonClient redisson;

    public JwtAuthFilter(JwtUtil jwtUtil, RedissonClient redisson) {
        this.jwtUtil = jwtUtil;
        this.redisson = redisson;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String token = null;

        // 1. Header: Authorization Bearer xxx（标准方式）
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        }

        // 2. Query Param: ?token=xxx（SSE EventSource 不支持自定义 Header，通过查询参数传 token）
        if (token == null) {
            token = request.getParameter("token");
        }

        if (token != null && jwtUtil.validateToken(token)) {
            // 检查 JWT 黑名单
            String tokenHash = DigestUtils.md5DigestAsHex(token.getBytes());
            RBucket<String> bucket = redisson.getBucket("paperai:jwt:blacklist:" + tokenHash);
            if (!bucket.isExists()) {
                Long userId = jwtUtil.getUserIdFromToken(token);
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList());
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        chain.doFilter(request, response);
    }
}
