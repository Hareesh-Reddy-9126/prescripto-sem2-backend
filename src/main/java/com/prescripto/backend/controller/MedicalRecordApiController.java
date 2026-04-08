package com.prescripto.backend.controller;

import com.prescripto.backend.model.AppointmentEntity;
import com.prescripto.backend.model.LabReportEntity;
import com.prescripto.backend.model.PrescriptionEntity;
import com.prescripto.backend.repository.AppointmentRepository;
import com.prescripto.backend.repository.LabReportRepository;
import com.prescripto.backend.repository.PharmacyOrderRepository;
import com.prescripto.backend.repository.PrescriptionRepository;
import com.prescripto.backend.service.AuthService;
import com.prescripto.backend.service.FileUploadService;
import com.prescripto.backend.service.MapperService;
import com.prescripto.backend.service.NotificationService;
import com.prescripto.backend.util.ApiResponse;
import com.prescripto.backend.util.IdUtil;
import com.prescripto.backend.util.JsonUtil;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/records")
public class MedicalRecordApiController {

    private final AppointmentRepository appointmentRepository;
    private final LabReportRepository labReportRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final PharmacyOrderRepository pharmacyOrderRepository;
    private final AuthService authService;
    private final MapperService mapperService;
    private final FileUploadService fileUploadService;
    private final NotificationService notificationService;
    private final JsonUtil jsonUtil;

    public MedicalRecordApiController(
        AppointmentRepository appointmentRepository,
        LabReportRepository labReportRepository,
        PrescriptionRepository prescriptionRepository,
        PharmacyOrderRepository pharmacyOrderRepository,
        AuthService authService,
        MapperService mapperService,
        FileUploadService fileUploadService,
        NotificationService notificationService,
        JsonUtil jsonUtil
    ) {
        this.appointmentRepository = appointmentRepository;
        this.labReportRepository = labReportRepository;
        this.prescriptionRepository = prescriptionRepository;
        this.pharmacyOrderRepository = pharmacyOrderRepository;
        this.authService = authService;
        this.mapperService = mapperService;
        this.fileUploadService = fileUploadService;
        this.notificationService = notificationService;
        this.jsonUtil = jsonUtil;
    }

    @PostMapping("/doctor/lab-report")
    public Map<String, Object> uploadLabReport(
        HttpServletRequest request,
        @RequestParam Map<String, String> form,
        @RequestPart(value = "report", required = false) MultipartFile reportFile
    ) {
        String docId = authService.requireDoctorId(request);

        String appointmentId = form.get("appointmentId");
        String title = form.get("title");
        String description = form.get("description");

        if (isBlank(appointmentId) || isBlank(title)) {
            return ApiResponse.failure("Appointment and title are required");
        }

        AppointmentEntity appointment = appointmentRepository.findById(appointmentId).orElse(null);
        if (appointment == null) {
            return ApiResponse.failure("Appointment not found");
        }

        if (!docId.equals(appointment.getDocId())) {
            return ApiResponse.failure("Unauthorized");
        }

        String fileUrl = fileUploadService.uploadAnyOrFallback(reportFile, "prescripto/lab-reports");

        Map<String, Object> metadata = new HashMap<>();
        if (reportFile != null && !reportFile.isEmpty()) {
            metadata.put("mimetype", reportFile.getContentType());
            metadata.put("size", reportFile.getSize());
        }

        Instant now = Instant.now();
        LabReportEntity labReport = LabReportEntity.builder()
            .id(IdUtil.objectId())
            .appointmentId(appointmentId)
            .userId(appointment.getUserId())
            .docId(docId)
            .title(title)
            .description(description)
            .fileUrl(fileUrl)
            .uploadedBy("doctor")
            .metadataJson(jsonUtil.toJson(metadata))
            .createdAt(now)
            .updatedAt(now)
            .build();

        labReportRepository.save(labReport);

        notificationService.notifyPatient(
            appointment.getUserId(),
            "New lab report shared",
            title + " is now available in your health records.",
            Map.of("appointmentId", appointment.getId(), "reportId", labReport.getId())
        );

        Map<String, Object> response = ApiResponse.success();
        response.put("report", mapperService.labReport(labReport));
        return response;
    }

    @GetMapping("/doctor/appointment/{appointmentId}")
    public Map<String, Object> doctorAppointmentRecords(HttpServletRequest request, @PathVariable String appointmentId) {
        String docId = authService.requireDoctorId(request);

        AppointmentEntity appointment = appointmentRepository.findById(appointmentId).orElse(null);
        if (appointment == null) {
            return ApiResponse.failure("Appointment not found");
        }

        if (!docId.equals(appointment.getDocId())) {
            return ApiResponse.failure("Unauthorized");
        }

        PrescriptionEntity prescription = prescriptionRepository.findByAppointmentId(appointmentId).orElse(null);
        List<Map<String, Object>> labReports = labReportRepository.findByAppointmentIdOrderByCreatedAtDesc(appointmentId).stream().map(mapperService::labReport).toList();

        Map<String, Object> response = ApiResponse.success();
        response.put("appointment", mapperService.appointment(appointment));
        response.put("prescription", prescription == null ? null : mapperService.prescription(prescription));
        response.put("labReports", labReports);
        return response;
    }

    @PostMapping("/patient/timeline")
    public Map<String, Object> patientTimeline(HttpServletRequest request, @RequestBody(required = false) Map<String, Object> body) {
        String userId = authService.requireUserId(request);

        List<Map<String, Object>> appointments = appointmentRepository.findByUserId(userId)
            .stream()
            .sorted((a, b) -> Long.compare(b.getDate() == null ? 0L : b.getDate(), a.getDate() == null ? 0L : a.getDate()))
            .map(mapperService::appointment)
            .toList();

        List<Map<String, Object>> prescriptions = prescriptionRepository.findByUserIdOrderByCreatedAtDesc(userId).stream().map(mapperService::prescription).toList();
        List<Map<String, Object>> labReports = labReportRepository.findByUserIdOrderByCreatedAtDesc(userId).stream().map(mapperService::labReport).toList();
        List<Map<String, Object>> pharmacyOrders = pharmacyOrderRepository.findByUserIdOrderByCreatedAtDesc(userId).stream().map(mapperService::pharmacyOrder).toList();

        Map<String, Object> timeline = new HashMap<>();
        timeline.put("appointments", appointments);
        timeline.put("prescriptions", prescriptions);
        timeline.put("labReports", labReports);
        timeline.put("pharmacyOrders", pharmacyOrders);

        Map<String, Object> response = ApiResponse.success();
        response.put("timeline", timeline);
        return response;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
