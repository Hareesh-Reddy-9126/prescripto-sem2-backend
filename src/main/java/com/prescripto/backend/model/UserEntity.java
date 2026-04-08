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
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(columnDefinition = "LONGTEXT")
    private String image;

    private String phone;

    @Column(name = "address_json", columnDefinition = "LONGTEXT")
    private String addressJson;

    @Column(name = "delivery_addresses_json", columnDefinition = "LONGTEXT")
    private String deliveryAddressesJson;

    @Column(name = "default_pharmacy_id", length = 64)
    private String defaultPharmacyId;

    private String gender;

    private String dob;

    @Column(nullable = false)
    private String password;
}
