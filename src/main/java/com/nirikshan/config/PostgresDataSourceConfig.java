package com.nirikshan.config;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.util.StringUtils;

/**
 * Allows Railway's DATABASE_URL to be used without requiring developers to
 * manually prepend the JDBC scheme.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DataSourceProperties.class)
@Profile({"prod", "local-postgres"})
public class PostgresDataSourceConfig {

    @Bean
    @Primary
    public DataSource dataSource(DataSourceProperties properties) {
        String url = properties.getUrl();
        if (!StringUtils.hasText(url)) {
            throw new IllegalStateException("No PostgreSQL URL configured. Set DATABASE_URL or SPRING_DATASOURCE_URL for the active PostgreSQL profile.");
        }
        if (url != null && (url.startsWith("postgresql://") || url.startsWith("postgres://"))) {
            properties.setUrl("jdbc:" + url);
            // Credentials embedded in Railway's URL take precedence over the
            // optional split username/password variables.
            properties.setUsername(null);
            properties.setPassword(null);
        }
        return properties.initializeDataSourceBuilder().build();
    }
}
