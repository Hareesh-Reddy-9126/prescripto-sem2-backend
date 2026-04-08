package com.prescripto.backend.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileUploadService {

    private final Cloudinary cloudinary;

    public FileUploadService(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    public String uploadImageOrFallback(MultipartFile file) {
        return uploadOrFallback(file, "image", "prescripto/images");
    }

    public String uploadAnyOrFallback(MultipartFile file, String folder) {
        return uploadOrFallback(file, "auto", folder);
    }

    private String uploadOrFallback(MultipartFile file, String resourceType, String folder) {
        if (file == null || file.isEmpty()) {
            return null;
        }

        if (!isConfigured()) {
            return file.getOriginalFilename();
        }

        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("prescripto-upload-", "-" + safeName(file.getOriginalFilename()));
            file.transferTo(tempFile.toFile());

            @SuppressWarnings("unchecked")
            Map<String, Object> result = cloudinary.uploader().upload(
                tempFile.toFile(),
                ObjectUtils.asMap("resource_type", resourceType, "folder", folder)
            );

            Object secureUrl = result.get("secure_url");
            return secureUrl == null ? file.getOriginalFilename() : String.valueOf(secureUrl);
        } catch (Exception ex) {
            return file.getOriginalFilename();
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                    // no-op
                }
            }
        }
    }

    private boolean isConfigured() {
        if (cloudinary == null || cloudinary.config == null) {
            return false;
        }

        Object cloudName = cloudinary.config.cloudName;
        Object apiKey = cloudinary.config.apiKey;
        Object apiSecret = cloudinary.config.apiSecret;
        return cloudName != null && apiKey != null && apiSecret != null;
    }

    private String safeName(String value) {
        if (value == null || value.isBlank()) {
            return "upload.bin";
        }
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
