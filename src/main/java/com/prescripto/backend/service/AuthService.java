package com.prescripto.backend.service;

import com.prescripto.backend.model.PharmacyEntity;
import com.prescripto.backend.repository.PharmacyRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final JwtService jwtService;

    @Value("${app.admin.email:}")
    private String adminEmail;

    @Value("${app.admin.password:}")
    private String adminPassword;

    public AuthService(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    public String requireUserId(HttpServletRequest request) {
        String token = request.getHeader("token");
        if (isBlank(token)) {
            throw new IllegalArgumentException("Not Authorized Login Again");
        }
        String id = jwtService.extractId(token);
        if (isBlank(id)) {
            throw new IllegalArgumentException("Not Authorized Login Again");
        }
        return id;
    }

    public String requireDoctorId(HttpServletRequest request) {
        String token = request.getHeader("dtoken");
        if (isBlank(token)) {
            throw new IllegalArgumentException("Not Authorized Login Again");
        }
        String id = jwtService.extractId(token);
        if (isBlank(id)) {
            throw new IllegalArgumentException("Not Authorized Login Again");
        }
        return id;
    }

    public String requireAdmin(HttpServletRequest request) {
        String token = request.getHeader("atoken");
        if (isBlank(token)) {
            throw new IllegalArgumentException("Not Authorized Login Again");
        }
        String decoded = jwtService.extractRaw(token);
        String normalizedAdminEmail = normalizeConfigValue(adminEmail);
        String normalizedAdminPassword = normalizeConfigValue(adminPassword);
        String expected = (normalizedAdminEmail == null ? "" : normalizedAdminEmail) + (normalizedAdminPassword == null ? "" : normalizedAdminPassword);
        if (!expected.equals(decoded)) {
            throw new IllegalArgumentException("Not Authorized Login Again");
        }
        return decoded;
    }

    private String normalizeConfigValue(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() >= 2) {
            if ((normalized.startsWith("\"") && normalized.endsWith("\"")) || (normalized.startsWith("'") && normalized.endsWith("'"))) {
                normalized = normalized.substring(1, normalized.length() - 1);
            }
        }
        return normalized;
    }

    public PharmacyEntity requirePharmacist(HttpServletRequest request, PharmacyRepository pharmacyRepository) {
        String token = request.getHeader("token");
        if (isBlank(token)) {
            throw new IllegalArgumentException("Not Authorized. Login Again.");
        }

        String pharmacyId = jwtService.extractId(token);
        if (isBlank(pharmacyId)) {
            throw new IllegalArgumentException("Invalid token");
        }

        Optional<PharmacyEntity> pharmacyOptional = pharmacyRepository.findById(pharmacyId);
        if (pharmacyOptional.isEmpty()) {
            throw new IllegalArgumentException("Pharmacy not found");
        }

        PharmacyEntity pharmacy = pharmacyOptional.get();
        if (!Boolean.TRUE.equals(pharmacy.getIsApproved())) {
            throw new IllegalArgumentException("Pharmacy pending approval");
        }
        if (!Boolean.TRUE.equals(pharmacy.getIsActive())) {
            throw new IllegalArgumentException("Pharmacy account disabled");
        }

        return pharmacy;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
