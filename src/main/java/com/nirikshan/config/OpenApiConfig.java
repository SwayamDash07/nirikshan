package com.nirikshan.config;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@Configuration public class OpenApiConfig {
    @Bean public OpenAPI nirikshanOpenAPI() { return new OpenAPI().info(new Info().title("Nirikshan API").version("v1").description("Crowd risk ingestion and alert APIs")); }
}
