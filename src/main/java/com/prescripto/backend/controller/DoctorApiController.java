package com.prescripto.backend.controller;

import com.prescripto.backend.model.AppointmentEntity;
import com.prescripto.backend.model.DoctorEntity;
import com.prescripto.backend.model.DoctorRequestEntity;
import com.prescripto.backend.model.PrescriptionEntity;
import com.prescripto.backend.repository.AppointmentRepository;
import com.prescripto.backend.repository.DoctorRepository;
import com.prescripto.backend.repository.DoctorRequestRepository;
import com.prescripto.backend.repository.PrescriptionRepository;
import com.prescripto.backend.service.AuthService;
import com.prescripto.backend.service.JwtService;
import com.prescripto.backend.service.MapperService;
import com.prescripto.backend.service.NotificationService;
import com.prescripto.backend.util.ApiResponse;
import com.prescripto.backend.util.IdUtil;
import com.prescripto.backend.util.JsonUtil;
import com.prescripto.backend.util.RequestUtil;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/doctor")
public class DoctorApiController {

    private final DoctorRepository doctorRepository;
    private final AppointmentRepository appointmentRepository;
    private final DoctorRequestRepository doctorRequestRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthService authService;
    private final MapperService mapperService;
    private final NotificationService notificationService;
    private final JsonUtil jsonUtil;

    public DoctorApiController(
        DoctorRepository doctorRepository,
        AppointmentRepository appointmentRepository,
        DoctorRequestRepository doctorRequestRepository,
        PrescriptionRepository prescriptionRepository,
        PasswordEncoder passwordEncoder,
        JwtService jwtService,
        AuthService authService,
        MapperService mapperService,
        NotificationService notificationService,
        JsonUtil jsonUtil
    ) {
        this.doctorRepository = doctorRepository;
        this.appointmentRepository = appointmentRepository;
        this.doctorRequestRepository = doctorRequestRepository;
        this.prescriptionRepository = prescriptionRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authService = authService;
        this.mapperService = mapperService;
        this.notificationService = notificationService;
        this.jsonUtil = jsonUtil;
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, Object> body) {
        String email = RequestUtil.str(body, "email");
        String password = RequestUtil.str(body, "password");

        Optional<DoctorEntity> doctorOptional = doctorRepository.findByEmail(email == null ? "" : email.toLowerCase(Locale.ROOT));
        if (doctorOptional.isEmpty()) {
            doctorOptional = doctorRepository.findByEmail(email);
        }

        if (doctorOptional.isEmpty()) {
            return ApiResponse.failure("Invalid credentials");
        }

        DoctorEntity doctor = doctorOptional.get();
        if (!passwordEncoder.matches(password == null ? "" : password, doctor.getPassword())) {
            return ApiResponse.failure("Invalid credentials");
        }

        Map<String, Object> response = ApiResponse.success();
        response.put("token", jwtService.generateIdToken(doctor.getId()));
        return response;
    }

    @PostMapping("/request")
    public Map<String, Object> requestDoctor(@RequestBody Map<String, Object> body) {
        String name = RequestUtil.str(body, "name");
        String email = RequestUtil.str(body, "email");
        String phone = RequestUtil.str(body, "phone");
        String speciality = RequestUtil.str(body, "speciality");
        String message = RequestUtil.str(body, "message");

        if (isBlank(name) || isBlank(email)) {
            return ApiResponse.failure("Name and email are required");
        }

        if (doctorRequestRepository.findByEmail(email).isPresent()) {
            return ApiResponse.failure("A request with this email already exists");
        }

        DoctorRequestEntity request = DoctorRequestEntity.builder()
            .id(IdUtil.objectId())
            .name(name)
            .email(email)
            .phone(phone)
            .speciality(speciality)
            .message(message)
            .status("pending")
            .createdAt(Instant.now())
            .build();

        doctorRequestRepository.save(request);

        Map<String, Object> response = ApiResponse.success();
        response.put("message", "Request submitted. Admin will review and create your account.");
        return response;
    }

    @PostMapping("/cancel-appointment")
    public Map<String, Object> cancelAppointment(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        String docId = authService.requireDoctorId(request);
        String appointmentId = RequestUtil.str(body, "appointmentId");

        AppointmentEntity appointment = appointmentRepository.findById(appointmentId).orElse(null);
        if (appointment != null && docId.equals(appointment.getDocId())) {
            appointment.setCancelled(true);
            appointmentRepository.save(appointment);
            Map<String, Object> response = ApiResponse.success();
            response.put("message", "Appointment Cancelled");
            return response;
        }

        return ApiResponse.failure("Appointment Cancelled");
    }

    @GetMapping("/appointments")
    public Map<String, Object> appointments(HttpServletRequest request) {
        String docId = authService.requireDoctorId(request);
        List<Map<String, Object>> appointments = appointmentRepository.findByDocId(docId)
            .stream()
            .map(mapperService::appointment)
            .toList();

        Map<String, Object> response = ApiResponse.success();
        response.put("appointments", appointments);
        return response;
    }

    @GetMapping("/list")
    public Map<String, Object> list() {
        List<Map<String, Object>> doctors = doctorRepository.findAll().stream().map(doctor -> {
            Map<String, Object> mapped = mapperService.doctor(doctor, false);
            mapped.remove("email");
            return mapped;
        }).toList();

        Map<String, Object> response = ApiResponse.success();
        response.put("doctors", doctors);
        return response;
    }

    @PostMapping("/change-availability")
    public Map<String, Object> changeAvailability(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        String docId = authService.requireDoctorId(request);
        String bodyDocId = RequestUtil.str(body, "docId");
        String effectiveDocId = isBlank(bodyDocId) ? docId : bodyDocId;

        DoctorEntity doctor = doctorRepository.findById(effectiveDocId).orElseThrow(() -> new IllegalArgumentException("Doctor not found"));
        doctor.setAvailable(!Boolean.TRUE.equals(doctor.getAvailable()));
        doctorRepository.save(doctor);

        Map<String, Object> response = ApiResponse.success();
        response.put("message", "Availablity Changed");
        return response;
    }

    @PostMapping("/complete-appointment")
    public Map<String, Object> completeAppointment(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        String docId = authService.requireDoctorId(request);
        String appointmentId = RequestUtil.str(body, "appointmentId");

        AppointmentEntity appointment = appointmentRepository.findById(appointmentId).orElse(null);
        if (appointment != null && docId.equals(appointment.getDocId())) {
            appointment.setIsCompleted(true);
            appointmentRepository.save(appointment);
            Map<String, Object> response = ApiResponse.success();
            response.put("message", "Appointment Completed");
            return response;
        }

        return ApiResponse.failure("Appointment Cancelled");
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard(HttpServletRequest request) {
        String docId = authService.requireDoctorId(request);
        List<AppointmentEntity> appointments = appointmentRepository.findByDocId(docId);

        double earnings = 0D;
        Map<String, Boolean> uniquePatients = new HashMap<>();
        for (AppointmentEntity appointment : appointments) {
            if (Boolean.TRUE.equals(appointment.getIsCompleted()) || Boolean.TRUE.equals(appointment.getPayment())) {
                earnings += appointment.getAmount() == null ? 0D : appointment.getAmount();
            }
            uniquePatients.put(appointment.getUserId(), true);
        }

        List<Map<String, Object>> latest = appointments.stream().map(mapperService::appointment).toList();

        Map<String, Object> dashData = new HashMap<>();
        dashData.put("earnings", earnings);
        dashData.put("appointments", appointments.size());
        dashData.put("patients", uniquePatients.size());
        dashData.put("latestAppointments", latest);

        Map<String, Object> response = ApiResponse.success();
        response.put("dashData", dashData);
        return response;
    }

    @GetMapping("/profile")
    public Map<String, Object> profile(HttpServletRequest request) {
        String docId = authService.requireDoctorId(request);
        DoctorEntity doctor = doctorRepository.findById(docId).orElseThrow(() -> new IllegalArgumentException("Doctor not found"));

        Map<String, Object> response = ApiResponse.success();
        response.put("profileData", mapperService.doctor(doctor, false));
        return response;
    }

    @PostMapping("/update-profile")
    public Map<String, Object> updateProfile(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        String docId = authService.requireDoctorId(request);
        DoctorEntity doctor = doctorRepository.findById(docId).orElseThrow(() -> new IllegalArgumentException("Doctor not found"));

        Double fees = RequestUtil.dbl(body, "fees");
        Boolean available = RequestUtil.bool(body, "available");
        Object address = body.get("address");

        if (fees != null) {
            doctor.setFees(fees);
        }
        if (available != null) {
            doctor.setAvailable(available);
        }
        if (address != null) {
            if (address instanceof String addressString) {
                Object parsed = jsonUtil.toObject(addressString);
                doctor.setAddressJson(jsonUtil.toJson(parsed == null ? addressString : parsed));
            } else {
                doctor.setAddressJson(jsonUtil.toJson(address));
            }
        }

        doctorRepository.save(doctor);

        Map<String, Object> response = ApiResponse.success();
        response.put("message", "Profile Updated");
        return response;
    }

    @PostMapping("/prescription/save")
    public Map<String, Object> savePrescription(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        String docId = authService.requireDoctorId(request);

        String appointmentId = RequestUtil.str(body, "appointmentId");
        AppointmentEntity appointment = appointmentRepository.findById(appointmentId).orElse(null);
        if (appointment == null) {
            return ApiResponse.failure("Appointment not found");
        }

        if (!docId.equals(appointment.getDocId())) {
            return ApiResponse.failure("Unauthorized");
        }

        DoctorEntity doctor = doctorRepository.findById(docId).orElse(null);
        if (doctor == null) {
            return ApiResponse.failure("Doctor not found");
        }

        PrescriptionEntity prescription = prescriptionRepository.findByAppointmentId(appointmentId).orElseGet(() -> PrescriptionEntity.builder()
            .id(IdUtil.objectId())
            .appointmentId(appointmentId)
            .createdAt(Instant.now())
            .build());

        prescription.setAppointmentId(appointmentId);
        prescription.setUserId(appointment.getUserId());
        prescription.setDocId(docId);
        prescription.setDiagnosis(RequestUtil.str(body, "diagnosis"));
        prescription.setClinicalNotes(RequestUtil.str(body, "clinicalNotes"));
        prescription.setMedicationsJson(jsonUtil.toJson(normalizeArray(body.get("medications"))));
        prescription.setInvestigationsJson(jsonUtil.toJson(normalizeArray(body.get("investigations"))));
        prescription.setLifestyleAdvice(RequestUtil.str(body, "lifestyleAdvice"));
        prescription.setAttachmentsJson(jsonUtil.toJson(normalizeArray(body.get("attachments"))));
        prescription.setPreferredPharmaciesJson(jsonUtil.toJson(normalizeArray(body.get("preferredPharmacies"))));
        prescription.setLastUpdatedBy(docId);

        String followUpDate = RequestUtil.str(body, "followUpDate");
        if (!isBlank(followUpDate)) {
            Instant parsedFollowUp = parseInstant(followUpDate);
            if (parsedFollowUp != null) {
                prescription.setFollowUpDate(parsedFollowUp);
            }
        }

        if (prescription.getIssuedAt() == null) {
            prescription.setIssuedAt(Instant.now());
        }
        prescription.setUpdatedAt(Instant.now());

        prescriptionRepository.save(prescription);

        appointment.setPrescriptionId(prescription.getId());
        appointmentRepository.save(appointment);

        notificationService.notifyPatient(
            appointment.getUserId(),
            "New prescription available",
            "Dr. " + doctor.getName() + " has shared a new prescription.",
            Map.of("appointmentId", appointmentId, "prescriptionId", prescription.getId())
        );

        Map<String, Object> response = ApiResponse.success();
        response.put("message", "Prescription saved");
        response.put("prescription", mapperService.prescription(prescription));
        return response;
    }

    @PostMapping("/prescription/detail")
    public Map<String, Object> prescriptionDetail(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        String docId = authService.requireDoctorId(request);
        String appointmentId = RequestUtil.str(body, "appointmentId");

        AppointmentEntity appointment = appointmentRepository.findById(appointmentId).orElse(null);
        if (appointment == null) {
            return ApiResponse.failure("Appointment not found");
        }

        if (!docId.equals(appointment.getDocId())) {
            return ApiResponse.failure("Unauthorized");
        }

        PrescriptionEntity prescription = prescriptionRepository.findByAppointmentId(appointmentId).orElse(null);
        Map<String, Object> response = ApiResponse.success();
        response.put("prescription", prescription == null ? null : mapperService.prescription(prescription));
        return response;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private List<Object> normalizeArray(Object value) {
        if (value == null) {
            return new ArrayList<>();
        }
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        if (value instanceof String text) {
            Object parsed = jsonUtil.toObject(text);
            if (parsed instanceof List<?> list) {
                return new ArrayList<>(list);
            }
        }
        return new ArrayList<>();
    }

    private Instant parseInstant(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }
}
