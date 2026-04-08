package com.prescripto.backend.repository;

import com.prescripto.backend.model.PharmacyEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PharmacyRepository extends JpaRepository<PharmacyEntity, String> {
    Optional<PharmacyEntity> findByEmail(String email);
    List<PharmacyEntity> findByIsApprovedAndIsActive(Boolean isApproved, Boolean isActive);
}
