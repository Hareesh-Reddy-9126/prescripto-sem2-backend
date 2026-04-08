package com.prescripto.backend.repository;

import com.prescripto.backend.model.PharmacyOrderEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PharmacyOrderRepository extends JpaRepository<PharmacyOrderEntity, String> {
    List<PharmacyOrderEntity> findByUserIdOrderByCreatedAtDesc(String userId);
    List<PharmacyOrderEntity> findByPharmacyIdOrderByCreatedAtDesc(String pharmacyId);
    Optional<PharmacyOrderEntity> findByOrderNumber(String orderNumber);
}
