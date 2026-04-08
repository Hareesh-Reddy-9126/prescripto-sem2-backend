package com.prescripto.backend.repository;

import com.prescripto.backend.model.DoctorRequestEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DoctorRequestRepository extends JpaRepository<DoctorRequestEntity, String> {
    Optional<DoctorRequestEntity> findByEmail(String email);
}
