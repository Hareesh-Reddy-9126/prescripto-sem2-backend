package com.prescripto.backend.controller;

import com.prescripto.backend.model.AppointmentEntity;
import com.prescripto.backend.model.DoctorEntity;
import com.prescripto.backend.model.UserEntity;
import com.prescripto.backend.repository.AppointmentRepository;
import com.prescripto.backend.repository.DoctorRepository;
import com.prescripto.backend.repository.UserRepository;
import com.prescripto.backend.service.AuthService;
import com.prescripto.backend.util.ApiResponse;
import com.prescripto.backend.util.JsonUtil;
import com.prescripto.backend.util.RequestUtil;
import jakarta.servlet.http.HttpServletRequest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/consultations")
public class ConsultationApiController {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AppointmentRepository appointmentRepository;
    private final DoctorRepository doctorRepository;
    private final UserRepository userRepository;
    private final AuthService authService;
    private final JsonUtil jsonUtil;

    public ConsultationApiController(
        AppointmentRepository appointmentRepository,
        DoctorRepository doctorRepository,
        UserRepository userRepository,
        AuthService authService,
        JsonUtil jsonUtil
    ) {
        this.appointmentRepository = appointmentRepository;
        this.doctorRepository = doctorRepository;
        this.userRepository = userRepository;
        this.authService = authService;
        this.jsonUtil = jsonUtil;
    }

    @PostMapping("/doctor/schedule")
    public Map<String, Object> doctorSchedule(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        String docId = authService.requireDoctorId(request);
        String appointmentId = RequestUtil.str(body, "appointmentId");

        AppointmentEntity appointment = appointmentRepository.findById(appointmentId).orElse(null);
        if (appointment == null) {
            return ApiResponse.failure("Appointment not found");
        }

        if (!docId.equals(appointment.getDocId())) {
            return ApiResponse.failure("Unauthorized");
        }

        Map<String, Object> consultation = getConsultationMap(appointment);
        if (isBlank(stringValue(consultation.get("roomCode")))) {
            consultation.put("roomCode", generateRoomCode());
            consultation.put("status", "scheduled");
        } else if (isBlank(stringValue(consultation.get("status"))) || "not_scheduled".equals(consultation.get("status"))) {
            consultation.put("status", "scheduled");
        }

        appointment.setConsultationJson(jsonUtil.toJson(consultation));
        appointmentRepository.save(appointment);

        Map<String, Object> mapped = sanitizeConsultation(appointment);

        DoctorEntity doctor = doctorRepository.findById(appointment.getDocId()).orElse(null);
        UserEntity patient = userRepository.findById(appointment.getUserId()).orElse(null);

        Map<String, Object> doctorSummary = new HashMap<>();
        if (doctor != null) {
            doctorSummary.put("name", doctor.getName());
            doctorSummary.put("speciality", doctor.getSpeciality());
        }

        Map<String, Object> patientSummary = new HashMap<>();
        if (patient != null) {
            patientSummary.put("name", patient.getName());
        }

        mapped.put("doctor", doctorSummary);
        mapped.put("patient", patientSummary);

        Map<String, Object> response = ApiResponse.success();
        response.put("consultation", mapped);
        return response;
    }

    @PostMapping("/doctor/details")
    public Map<String, Object> doctorDetails(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        String docId = authService.requireDoctorId(request);
        return detailsInternal(body, docId);
    }

    @PostMapping("/patient/details")
    public Map<String, Object> patientDetails(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        String userId = authService.requireUserId(request);
        return detailsInternal(body, userId);
    }

    @PostMapping("/doctor/start")
    public Map<String, Object> start(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        String docId = authService.requireDoctorId(request);
        String appointmentId = RequestUtil.str(body, "appointmentId");

        AppointmentEntity appointment = appointmentRepository.findById(appointmentId).orElse(null);
        if (appointment == null) {
            return ApiResponse.failure("Appointment not found");
        }

        if (!docId.equals(appointment.getDocId())) {
            return ApiResponse.failure("Unauthorized");
        }

        Map<String, Object> consultation = getConsultationMap(appointment);
        if (isBlank(stringValue(consultation.get("roomCode")))) {
            consultation.put("roomCode", generateRoomCode());
        }

        consultation.put("status", "live");
        if (consultation.get("startedAt") == null) {
            consultation.put("startedAt", Instant.now().toString());
        }

        appointment.setConsultationJson(jsonUtil.toJson(consultation));
        appointmentRepository.save(appointment);

        Map<String, Object> response = ApiResponse.success();
        response.put("consultation", sanitizeConsultation(appointment));
        return response;
    }

    @PostMapping("/doctor/complete")
    public Map<String, Object> complete(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        String docId = authService.requireDoctorId(request);
        String appointmentId = RequestUtil.str(body, "appointmentId");

        AppointmentEntity appointment = appointmentRepository.findById(appointmentId).orElse(null);
        if (appointment == null) {
            return ApiResponse.failure("Appointment not found");
        }

        if (!docId.equals(appointment.getDocId())) {
            return ApiResponse.failure("Unauthorized");
        }

        Map<String, Object> consultation = getConsultationMap(appointment);
        consultation.put("status", "completed");
        consultation.put("endedAt", Instant.now().toString());
        consultation.put("summary", RequestUtil.str(body, "summary"));
        consultation.put("notesForPatient", RequestUtil.str(body, "notesForPatient"));
        consultation.put("notesForInternal", RequestUtil.str(body, "notesForInternal"));

        String followUpDate = RequestUtil.str(body, "followUpDate");
        if (!isBlank(followUpDate)) {
            consultation.put("followUpDate", followUpDate);
        }

        appointment.setConsultationJson(jsonUtil.toJson(consultation));
        appointment.setIsCompleted(true);
        appointmentRepository.save(appointment);

        Map<String, Object> response = ApiResponse.success();
        response.put("consultation", sanitizeConsultation(appointment));
        return response;
    }

    private Map<String, Object> detailsInternal(Map<String, Object> body, String requesterId) {
        String appointmentId = RequestUtil.str(body, "appointmentId");

        AppointmentEntity appointment = appointmentRepository.findById(appointmentId).orElse(null);
        if (appointment == null) {
            return ApiResponse.failure("Appointment not found");
        }

        if (!requesterId.equals(appointment.getUserId()) && !requesterId.equals(appointment.getDocId())) {
            return ApiResponse.failure("Unauthorized");
        }

        Map<String, Object> response = ApiResponse.success();
        response.put("consultation", sanitizeConsultation(appointment));
        return response;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getConsultationMap(AppointmentEntity appointment) {
        Object parsed = jsonUtil.toObject(appointment.getConsultationJson());
        if (parsed instanceof Map<?, ?> map) {
            return new HashMap<>((Map<String, Object>) map);
        }
        return new HashMap<>();
    }

    private Map<String, Object> sanitizeConsultation(AppointmentEntity appointment) {
        Map<String, Object> consultation = getConsultationMap(appointment);
        Map<String, Object> mapped = new HashMap<>();

        mapped.put("roomCode", consultation.get("roomCode"));
        mapped.put("status", consultation.getOrDefault("status", "not_scheduled"));
        mapped.put("startedAt", consultation.get("startedAt"));
        mapped.put("endedAt", consultation.get("endedAt"));
        mapped.put("summary", consultation.get("summary"));
        mapped.put("followUpDate", consultation.get("followUpDate"));
        mapped.put("notesForPatient", consultation.get("notesForPatient"));
        mapped.put("notesForInternal", consultation.get("notesForInternal"));
        mapped.put("appointmentId", appointment.getId());
        mapped.put("slotDate", appointment.getSlotDate());
        mapped.put("slotTime", appointment.getSlotTime());
        mapped.put("doctorId", appointment.getDocId());
        mapped.put("patientId", appointment.getUserId());

        return mapped;
    }

    private String generateRoomCode() {
        byte[] bytes = new byte[6];
        RANDOM.nextBytes(bytes);
        return "prescripto-" + HexFormat.of().formatHex(bytes);
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
