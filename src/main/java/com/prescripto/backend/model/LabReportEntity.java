package com.prescripto.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "lab_reports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LabReportEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "appointment_id", nullable = false, length = 64)
    private String appointmentId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "doc_id", nullable = false, length = 64)
    private String docId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "LONGTEXT")
    private String description;

    @Column(name = "file_url", columnDefinition = "LONGTEXT")
    private String fileUrl;

    @Column(name = "uploaded_by")
    private String uploadedBy;

    @Column(name = "metadata_json", columnDefinition = "LONGTEXT")
    private String metadataJson;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
