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
@Table(name = "doctors")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DoctorEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(columnDefinition = "LONGTEXT")
    private String image;

    @Column(nullable = false)
    private String speciality;

    @Column(nullable = false)
    private String degree;

    @Column(nullable = false)
    private String experience;

    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String about;

    private Boolean available;

    private Double fees;

    @Column(name = "slots_booked_json", columnDefinition = "LONGTEXT")
    private String slotsBookedJson;

    @Column(name = "address_json", columnDefinition = "LONGTEXT")
    private String addressJson;

    private Long date;
}
