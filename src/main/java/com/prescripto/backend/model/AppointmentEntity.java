package com.prescripto.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "appointments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppointmentEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "doc_id", nullable = false, length = 64)
    private String docId;

    @Column(name = "slot_date", nullable = false)
    private String slotDate;

    @Column(name = "slot_time", nullable = false)
    private String slotTime;

    @Column(name = "user_data_json", nullable = false, columnDefinition = "LONGTEXT")
    private String userDataJson;

    @Column(name = "doc_data_json", nullable = false, columnDefinition = "LONGTEXT")
    private String docDataJson;

    @Column(nullable = false)
    private Double amount;

    @Column(nullable = false)
    private Long date;

    private Boolean cancelled;

    private Boolean payment;

    @Column(name = "is_completed")
    private Boolean isCompleted;

    @Column(name = "prescription_id", length = 64)
    private String prescriptionId;

    @Column(name = "pharmacy_order_id", length = 64)
    private String pharmacyOrderId;

    @Column(name = "consultation_json", columnDefinition = "LONGTEXT")
    private String consultationJson;
}
