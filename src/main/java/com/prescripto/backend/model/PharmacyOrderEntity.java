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
@Table(name = "pharmacy_orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PharmacyOrderEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "order_number", nullable = false, unique = true)
    private String orderNumber;

    @Column(name = "prescription_id", nullable = false, length = 64)
    private String prescriptionId;

    @Column(name = "appointment_id", nullable = false, length = 64)
    private String appointmentId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "pharmacy_id", nullable = false, length = 64)
    private String pharmacyId;

    @Column(nullable = false)
    private String status;

    @Column(name = "status_history_json", columnDefinition = "LONGTEXT")
    private String statusHistoryJson;

    @Column(name = "logistics_json", columnDefinition = "LONGTEXT")
    private String logisticsJson;

    @Column(name = "notes_for_patient", columnDefinition = "LONGTEXT")
    private String notesForPatient;

    @Column(name = "notes_for_internal", columnDefinition = "LONGTEXT")
    private String notesForInternal;

    @Column(name = "prescription_snapshot_json", columnDefinition = "LONGTEXT")
    private String prescriptionSnapshotJson;

    @Column(name = "patient_snapshot_json", columnDefinition = "LONGTEXT")
    private String patientSnapshotJson;

    @Column(name = "total_amount")
    private Double totalAmount;

    @Column(name = "payment_status")
    private String paymentStatus;

    @Column(name = "created_via")
    private String createdVia;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
