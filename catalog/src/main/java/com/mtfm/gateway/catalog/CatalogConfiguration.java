package com.mtfm.gateway.catalog;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * catalog 装配。JDBC / MyBatis / Flyway 只出现在本模块。
 */
@Configuration
@MapperScan("com.mtfm.gateway.catalog.mapper")
public class CatalogConfiguration {

    /**
     * 分页插件：按 datasource URL 自动识别方言；本地 H2 / MySQL / PostgreSQL 均可。
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor();
        pagination.setMaxLimit(100L);
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }
}
