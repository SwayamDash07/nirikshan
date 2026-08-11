package com.nirikshan.config;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.servlet.config.annotation.*;
import java.nio.file.Path;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Value("${nirikshan.cv.pipeline-dir:cv-pipeline}") private String pipelineDir;
    @Override public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**").allowedOrigins("http://localhost:3000", "*").allowedMethods("GET", "POST", "PATCH", "OPTIONS").allowedHeaders("*");
    }
    @Override public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String outputsLocation = Path.of(pipelineDir).toAbsolutePath().normalize().resolve("outputs").toUri().toString();
        registry.addResourceHandler("/job-files/**").addResourceLocations(outputsLocation);
    }
}
