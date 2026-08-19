package com.nirikshan.config;

import javax.sql.DataSource;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.util.StringUtils;

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
            applyPostgresUrl(properties, url);
        }
        return properties.initializeDataSourceBuilder().build();
    }

    private void applyPostgresUrl(DataSourceProperties properties, String value) {
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("DATABASE_URL is not a valid PostgreSQL URL", exception);
        }
        String userInfo = uri.getRawUserInfo();
        if (StringUtils.hasText(userInfo)) {
            int separator = userInfo.indexOf(':');
            String username = separator < 0 ? userInfo : userInfo.substring(0, separator);
            String password = separator < 0 ? "" : userInfo.substring(separator + 1);
            properties.setUsername(URLDecoder.decode(username, StandardCharsets.UTF_8));
            properties.setPassword(URLDecoder.decode(password, StandardCharsets.UTF_8));
        }
        StringBuilder jdbcUrl = new StringBuilder("jdbc:postgresql://");
        jdbcUrl.append(uri.getHost());
        if (uri.getPort() >= 0) jdbcUrl.append(':').append(uri.getPort());
        if (uri.getRawPath() != null) jdbcUrl.append(uri.getRawPath());
        if (uri.getRawQuery() != null) jdbcUrl.append('?').append(uri.getRawQuery());
        properties.setUrl(jdbcUrl.toString());
    }
}
