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
@Table(name = "pharmacies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PharmacyEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(name = "owner_name", nullable = false)
    private String ownerName;

    @Column(nullable = false)
    private String phone;

    @Column(name = "alternate_phone")
    private String alternatePhone;

    @Column(name = "address_json", nullable = false, columnDefinition = "LONGTEXT")
    private String addressJson;

    @Column(name = "license_number", nullable = false, unique = true)
    private String licenseNumber;

    @Column(name = "license_documents_json", columnDefinition = "LONGTEXT")
    private String licenseDocumentsJson;

    @Column(name = "gst_number")
    private String gstNumber;

    @Column(name = "delivery_options_json", columnDefinition = "LONGTEXT")
    private String deliveryOptionsJson;

    @Column(name = "operating_hours_json", columnDefinition = "LONGTEXT")
    private String operatingHoursJson;

    @Column(name = "service_radius_km")
    private Double serviceRadiusKm;

    @Column(name = "is_approved")
    private Boolean isApproved;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "approved_by")
    private String approvedBy;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "moderation_notes", columnDefinition = "LONGTEXT")
    private String moderationNotes;

    @Column(name = "payout_details_json", columnDefinition = "LONGTEXT")
    private String payoutDetailsJson;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
