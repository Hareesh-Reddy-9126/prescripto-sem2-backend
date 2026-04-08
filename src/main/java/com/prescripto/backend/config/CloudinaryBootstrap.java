package com.prescripto.backend.config;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CloudinaryBootstrap {

    @Value("${app.cloudinary.name:}")
    private String cloudName;

    @Value("${app.cloudinary.api-key:}")
    private String apiKey;

    @Value("${app.cloudinary.secret-key:}")
    private String secretKey;

    private Cloudinary cloudinary;

    @PostConstruct
    public void init() {
        if (isBlank(cloudName) || isBlank(apiKey) || isBlank(secretKey)) {
            System.out.println("[cloudinary] Cloudinary credentials not fully configured. Uploads will be skipped/fallback.");
            cloudinary = new Cloudinary(ObjectUtils.emptyMap());
            return;
        }

        cloudinary = new Cloudinary(ObjectUtils.asMap(
            "cloud_name", cloudName,
            "api_key", apiKey,
            "api_secret", secretKey
        ));
    }

    @Bean
    public Cloudinary cloudinary() {
        return cloudinary;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
