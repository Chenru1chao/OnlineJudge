package com.chenru1chao.interceptor;

import cn.hutool.core.util.StrUtil;
import com.chenru1chao.config.JwtProperties;
import com.chenru1chao.util.JwtUtil;
import com.chenru1chao.util.UserContext;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class LoginInterceptor implements HandlerInterceptor {

    // Authorization 头按 HTTP 惯例是 "Bearer <token>"，前端 axios 拦截器一般也这么发
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtProperties jwtProperties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String jwt = request.getHeader(jwtProperties.getTokenName());
        if (StrUtil.isBlank(jwt)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }

        // 带前缀就剥掉，不带前缀（前端直接发裸 token）就原样用，两种都能跑
        if (StrUtil.startWithIgnoreCase(jwt, BEARER_PREFIX)) {
            jwt = jwt.substring(BEARER_PREFIX.length()).trim();
        }

        try {
            Claims claims = JwtUtil.parseJwt(jwtProperties.getKey(), jwt);
            // TODO: 后续需补充对应的常量类
            Integer userId = (Integer) claims.get("UserId");
            UserContext.set(userId);
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable Exception ex) throws Exception {
        // 这个地方一定要remove 不然会发生oom
        UserContext.remove();
    }
}
