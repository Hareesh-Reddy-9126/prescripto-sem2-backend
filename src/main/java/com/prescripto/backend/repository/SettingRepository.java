package com.prescripto.backend.repository;

import com.prescripto.backend.model.SettingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettingRepository extends JpaRepository<SettingEntity, String> {
}
