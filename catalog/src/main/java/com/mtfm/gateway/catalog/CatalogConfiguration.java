package com.mtfm.gateway.catalog;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.mtfm.gateway.spi.secret.SecretCodec;
import org.flywaydb.core.Flyway;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

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

    /**
     * 按 JDBC URL 选择 {@code db/migration/{h2,mysql,postgresql}}，避免默认路径混入三套方言。
     */
    @Bean
    public Flyway flyway(DataSource dataSource) {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration/" + dialectFromJdbcUrl(jdbcUrl(dataSource)))
                .load();
        flyway.migrate();
        return flyway;
    }

    /**
     * 默认恒等。嵌入系统提供自己的 {@link SecretCodec} Bean 即可加密落库。
     */
    @Bean
    @ConditionalOnMissingBean(SecretCodec.class)
    public SecretCodec secretCodec() {
        return SecretCodec.identity();
    }

    static String dialectFromJdbcUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("数据源 URL 为空，无法选择 Flyway 脚本目录");
        }
        String lower = url.toLowerCase();
        if (lower.startsWith("jdbc:h2:")) {
            return "h2";
        }
        if (lower.startsWith("jdbc:mysql:") || lower.startsWith("jdbc:mariadb:")) {
            return "mysql";
        }
        if (lower.startsWith("jdbc:postgresql:")) {
            return "postgresql";
        }
        throw new IllegalStateException("不支持的数据源方言，无法选择 Flyway 脚本: " + url);
    }

    private static String jdbcUrl(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            return connection.getMetaData().getURL();
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取数据源 URL", ex);
        }
    }
}
