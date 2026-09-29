package com.mp.aitrader.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 插件配置。
 *
 * <p>注册分页插件后，{@code mapper.selectPage(Page, Wrapper)} 会自动改写为带 LIMIT 的分页 SQL，
 * 无需再手工拼接 {@code .last("LIMIT x OFFSET y")} ——
 * 手工拼接既难维护，又在深分页时把 OFFSET 全表扫描的代价暴露给调用方。
 *
 * <p>该插件只在入参含 {@code IPage} 时生效，不影响其余查询。
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
