package com.mp.aitrader.interceptor;

import com.mp.aitrader.Constant.JwtClaimsConstant;
import com.mp.aitrader.Utils.JwtUtil;
import com.mp.aitrader.context.BaseContext;
import com.mp.aitrader.properties.JwtProperties;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@Slf4j
public class JwtInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private StringRedisTemplate redisTemplate;
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        log.info("进入拦截器...");
        if (!(handler instanceof HandlerMethod)){
            return true;
        }
        String token = request.getHeader(jwtProperties.getUserTokenName());
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        
        // 如果 token 为空
        if (token == null || token.isEmpty()) {
            // 对于 /moments/list 和 GET /moments/*/comments 接口，允许未登录访问
            String uri = request.getRequestURI();
            if (uri.contains("/moments/list") ||
                (uri.matches(".*/moments/\\d+/comments") && "GET".equalsIgnoreCase(request.getMethod()))) {
                return true;
            }
            // 其他接口必须登录
            response.setStatus(401);
            return false;
        }

        //验证令牌
        try {
            log.info("验证令牌：{}", token);

            //判断jwt黑名单
            Boolean isBlacklisted = redisTemplate.hasKey("blacklist:" + token);
            if (isBlacklisted) {
                log.info("令牌已注销：{}", token);
                response.setStatus(401);
                return false;
            }

            Claims claims = JwtUtil.parseJWT(jwtProperties.getUserSecretKey(), token);
            Long userId = Long.valueOf(claims.get(JwtClaimsConstant.USER_ID).toString());
            log.info("用户id：{}", userId);
            BaseContext.setCurrentId(userId);
            return true;
        }catch (Exception e){
            response.setStatus(401);
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        log.info("清理当前线程的变量：{}", BaseContext.getCurrentId());
        BaseContext.removeCurrentId();
    }
}
