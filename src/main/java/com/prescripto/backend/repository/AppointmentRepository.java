package com.prescripto.backend.repository;

import com.prescripto.backend.model.AppointmentEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppointmentRepository extends JpaRepository<AppointmentEntity, String> {
    List<AppointmentEntity> findByUserId(String userId);
    List<AppointmentEntity> findByDocId(String docId);
}
