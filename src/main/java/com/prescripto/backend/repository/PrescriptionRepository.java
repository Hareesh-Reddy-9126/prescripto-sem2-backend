package com.prescripto.backend.repository;

import com.prescripto.backend.model.PrescriptionEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrescriptionRepository extends JpaRepository<PrescriptionEntity, String> {
    Optional<PrescriptionEntity> findByAppointmentId(String appointmentId);
    List<PrescriptionEntity> findByUserIdOrderByCreatedAtDesc(String userId);
}
