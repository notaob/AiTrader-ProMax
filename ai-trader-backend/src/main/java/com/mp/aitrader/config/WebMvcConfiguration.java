package com.mp.aitrader.config;

import com.mp.aitrader.interceptor.JwtInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@Slf4j
public class WebMvcConfiguration implements WebMvcConfigurer {

    @Autowired
    private JwtInterceptor jwtInterceptor;

    /**
     * 注册自定义拦截器
     *
     * @param registry
     */
    public void addInterceptors(InterceptorRegistry registry) {
        log.info("开始注册自定义拦截器...");
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/user/**", "/moments/**", "/ai/**", "/market/**")
                .excludePathPatterns(
                        "/user/login/**",
                        "/user/register",
                        "/user/code",
                        "/user/login/sms",
                        "/user/resetPassword",
                        "/user/logout",
                        "/market/promotions"
                );

    }

    /**
     * 设置静态资源映射
     * @param registry
     */
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        log.info("开始设置静态资源映射...");
        registry.addResourceHandler("/upload/**").addResourceLocations("file:" + System.getProperty("user.dir") + "/upload/");
    }
}
