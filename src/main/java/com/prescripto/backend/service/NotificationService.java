package com.prescripto.backend.service;

import com.prescripto.backend.model.NotificationEntity;
import com.prescripto.backend.repository.NotificationRepository;
import com.prescripto.backend.util.IdUtil;
import com.prescripto.backend.util.JsonUtil;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final JsonUtil jsonUtil;

    public NotificationService(NotificationRepository notificationRepository, JsonUtil jsonUtil) {
        this.notificationRepository = notificationRepository;
        this.jsonUtil = jsonUtil;
    }

    public NotificationEntity notifyPatient(String userId, String title, String message, Map<String, Object> metadata) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        return create(userId, "patient", title, message, metadata);
    }

    public NotificationEntity notifyPharmacist(String pharmacyId, String title, String message, Map<String, Object> metadata) {
        if (pharmacyId == null || pharmacyId.isBlank()) {
            return null;
        }
        return create(pharmacyId, "pharmacist", title, message, metadata);
    }

    public NotificationEntity notifyDoctor(String doctorId, String title, String message, Map<String, Object> metadata) {
        if (doctorId == null || doctorId.isBlank()) {
            return null;
        }
        return create(doctorId, "doctor", title, message, metadata);
    }

    public NotificationEntity notifyAdmin(String adminId, String title, String message, Map<String, Object> metadata) {
        if (adminId == null || adminId.isBlank()) {
            return null;
        }
        return create(adminId, "admin", title, message, metadata);
    }

    private NotificationEntity create(String recipientId, String role, String title, String message, Map<String, Object> metadata) {
        Instant now = Instant.now();

        NotificationEntity notification = NotificationEntity.builder()
            .id(IdUtil.objectId())
            .recipientId(recipientId)
            .recipientRole(role)
            .title(title)
            .message(message)
            .metadataJson(jsonUtil.toJson(metadata == null ? Map.of() : metadata))
            .isRead(false)
            .createdAt(now)
            .updatedAt(now)
            .build();

        return notificationRepository.save(notification);
    }
}
