package com.heiguang.portfolio.migration.config;

import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
public class DataSourceConfig {
    @Bean @ConfigurationProperties("app.datasource.source")
    DataSourceProperties sourceDataSourceProperties() { return new DataSourceProperties(); }

    @Bean
    DataSource sourceDataSource(@Qualifier("sourceDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Bean @ConfigurationProperties("app.datasource.target")
    DataSourceProperties targetDataSourceProperties() { return new DataSourceProperties(); }

    @Bean
    DataSource targetDataSource(@Qualifier("targetDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Bean
    JdbcTemplate sourceJdbcTemplate(@Qualifier("sourceDataSource") DataSource dataSource) { return new JdbcTemplate(dataSource); }

    @Bean
    JdbcTemplate targetJdbcTemplate(@Qualifier("targetDataSource") DataSource dataSource) { return new JdbcTemplate(dataSource); }

    @Bean
    PlatformTransactionManager targetTransactionManager(@Qualifier("targetDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean
    TransactionTemplate targetTransactionTemplate(@Qualifier("targetTransactionManager") PlatformTransactionManager manager) {
        return new TransactionTemplate(manager);
    }
}
