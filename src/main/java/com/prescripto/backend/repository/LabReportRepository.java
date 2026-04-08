package com.prescripto.backend.repository;

import com.prescripto.backend.model.LabReportEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabReportRepository extends JpaRepository<LabReportEntity, String> {
    List<LabReportEntity> findByUserIdOrderByCreatedAtDesc(String userId);
    List<LabReportEntity> findByAppointmentIdOrderByCreatedAtDesc(String appointmentId);
}
