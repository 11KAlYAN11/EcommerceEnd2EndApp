package com.ecommerce.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * WHY THIS CLASS?
 *
 * After uploading an image to disk, the client needs to be able to GET it via HTTP.
 * Spring Boot by default only serves static files from classpath:/static/.
 *
 * This maps:
 *   GET /api/files/products/uuid.jpg
 *   → reads from: {uploadDir}/products/uuid.jpg on disk
 *
 * So a product's imageUrl = "/files/products/uuid.jpg"
 * Client can display: <img src="http://localhost:8080/api/files/products/uuid.jpg" />
 *
 * In production:
 *   Remove this class entirely.
 *   Files are on S3 → client fetches from S3 URL directly.
 *   Spring app never serves images — that's Nginx/CDN's job.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${file.upload.dir:uploads}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String absolutePath = Paths.get(uploadDir).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/files/**")
                .addResourceLocations(absolutePath);
    }
}
