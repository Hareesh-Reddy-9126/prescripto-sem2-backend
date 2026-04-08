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
@Table(name = "prescriptions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrescriptionEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "appointment_id", nullable = false, length = 64)
    private String appointmentId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "doc_id", nullable = false, length = 64)
    private String docId;

    @Column(columnDefinition = "LONGTEXT")
    private String diagnosis;

    @Column(name = "clinical_notes", columnDefinition = "LONGTEXT")
    private String clinicalNotes;

    @Column(name = "medications_json", columnDefinition = "LONGTEXT")
    private String medicationsJson;

    @Column(name = "investigations_json", columnDefinition = "LONGTEXT")
    private String investigationsJson;

    @Column(name = "follow_up_date")
    private Instant followUpDate;

    @Column(name = "lifestyle_advice", columnDefinition = "LONGTEXT")
    private String lifestyleAdvice;

    @Column(name = "attachments_json", columnDefinition = "LONGTEXT")
    private String attachmentsJson;

    @Column(name = "preferred_pharmacies_json", columnDefinition = "LONGTEXT")
    private String preferredPharmaciesJson;

    @Column(name = "issued_at")
    private Instant issuedAt;

    @Column(name = "last_updated_by", length = 64)
    private String lastUpdatedBy;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
