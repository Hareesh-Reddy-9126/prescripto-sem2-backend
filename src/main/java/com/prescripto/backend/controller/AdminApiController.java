package com.prescripto.backend.controller;

import com.prescripto.backend.model.AppointmentEntity;
import com.prescripto.backend.model.DoctorEntity;
import com.prescripto.backend.model.PharmacyEntity;
import com.prescripto.backend.model.PharmacyOrderEntity;
import com.prescripto.backend.model.SettingEntity;
import com.prescripto.backend.model.UserEntity;
import com.prescripto.backend.repository.AppointmentRepository;
import com.prescripto.backend.repository.DoctorRepository;
import com.prescripto.backend.repository.PharmacyOrderRepository;
import com.prescripto.backend.repository.PharmacyRepository;
import com.prescripto.backend.repository.SettingRepository;
import com.prescripto.backend.repository.UserRepository;
import com.prescripto.backend.service.AuthService;
import com.prescripto.backend.service.FileUploadService;
import com.prescripto.backend.service.JwtService;
import com.prescripto.backend.service.MapperService;
import com.prescripto.backend.service.NotificationService;
import com.prescripto.backend.util.ApiResponse;
import com.prescripto.backend.util.IdUtil;
import com.prescripto.backend.util.JsonUtil;
import com.prescripto.backend.util.RequestUtil;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin")
public class AdminApiController {

    private static final Map<String, Object> DEFAULT_PLATFORM_SETTINGS = Map.of(
        "consultationFee", 499,
        "cancellationWindowHours", 12,
        "videoProvider", "jitsi",
        "emergencyContact", "",
        "allowedDeliveryRadiusKm", 15,
        "security", Map.of("enforceMfa", false, "sessionTimeoutMinutes", 60)
    );

    private final AuthService authService;
    private final JwtService jwtService;
    private final AppointmentRepository appointmentRepository;
    private final DoctorRepository doctorRepository;
    private final UserRepository userRepository;
    private final PharmacyRepository pharmacyRepository;
    private final PharmacyOrderRepository pharmacyOrderRepository;
    private final SettingRepository settingRepository;
    private final MapperService mapperService;
    private final PasswordEncoder passwordEncoder;
    private final FileUploadService fileUploadService;
    private final NotificationService notificationService;
    private final JsonUtil jsonUtil;
    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    @Value("${app.admin.email:}")
    private String adminEmail;

    @Value("${app.admin.password:}")
    private String adminPassword;

    public AdminApiController(
        AuthService authService,
        JwtService jwtService,
        AppointmentRepository appointmentRepository,
        DoctorRepository doctorRepository,
        UserRepository userRepository,
        PharmacyRepository pharmacyRepository,
        PharmacyOrderRepository pharmacyOrderRepository,
        SettingRepository settingRepository,
        MapperService mapperService,
        PasswordEncoder passwordEncoder,
        FileUploadService fileUploadService,
        NotificationService notificationService,
        JsonUtil jsonUtil,
        JdbcTemplate jdbcTemplate,
        DataSource dataSource
    ) {
        this.authService = authService;
        this.jwtService = jwtService;
        this.appointmentRepository = appointmentRepository;
        this.doctorRepository = doctorRepository;
        this.userRepository = userRepository;
        this.pharmacyRepository = pharmacyRepository;
        this.pharmacyOrderRepository = pharmacyOrderRepository;
        this.settingRepository = settingRepository;
        this.mapperService = mapperService;
        this.passwordEncoder = passwordEncoder;
        this.fileUploadService = fileUploadService;
        this.notificationService = notificationService;
        this.jsonUtil = jsonUtil;
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, Object> body) {
        String email = RequestUtil.str(body, "email");
        String password = RequestUtil.str(body, "password");

        String expectedEmail = normalizeConfigValue(adminEmail);
        String expectedPassword = normalizeConfigValue(adminPassword);

        if (email != null && password != null && email.equals(expectedEmail) && password.equals(expectedPassword)) {
            Map<String, Object> response = ApiResponse.success();
            response.put("token", jwtService.generateRawToken(email + password));
            return response;
        }

        return ApiResponse.failure("Invalid credentials");
    }

    @GetMapping("/appointments")
    public Map<String, Object> appointments(HttpServletRequest request) {
        authService.requireAdmin(request);
        List<Map<String, Object>> appointments = appointmentRepository.findAll().stream().map(mapperService::appointment).toList();
        Map<String, Object> response = ApiResponse.success();
        response.put("appointments", appointments);
        return response;
    }

    @PostMapping("/cancel-appointment")
    public Map<String, Object> cancelAppointment(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        authService.requireAdmin(request);
        String appointmentId = RequestUtil.str(body, "appointmentId");
        appointmentRepository.findById(appointmentId).ifPresent(appointment -> {
            appointment.setCancelled(true);
            appointmentRepository.save(appointment);
        });

        Map<String, Object> response = ApiResponse.success();
        response.put("message", "Appointment Cancelled");
        return response;
    }

    @PostMapping("/add-doctor")
    public Map<String, Object> addDoctor(
        HttpServletRequest request,
        @RequestParam Map<String, String> form,
        @RequestPart(value = "image", required = false) MultipartFile image
    ) {
        authService.requireAdmin(request);

        String name = form.get("name");
        String email = form.get("email");
        String password = form.get("password");
        String speciality = form.get("speciality");
        String degree = form.get("degree");
        String experience = form.get("experience");
        String about = form.get("about");
        String fees = form.get("fees");
        String address = form.get("address");

        if (isBlank(name) || isBlank(email) || isBlank(password) || isBlank(speciality) || isBlank(degree) || isBlank(experience) || isBlank(about) || isBlank(fees) || isBlank(address)) {
            return ApiResponse.failure("Missing Details");
        }

        if (!email.contains("@")) {
            return ApiResponse.failure("Please enter a valid email");
        }

        if (password.length() < 8) {
            return ApiResponse.failure("Please enter a strong password");
        }

        String imageUrl = fileUploadService.uploadImageOrFallback(image);
        Object parsedAddress = jsonUtil.toObject(address);

        DoctorEntity doctor = DoctorEntity.builder()
            .id(IdUtil.objectId())
            .name(name)
            .email(email.toLowerCase(Locale.ROOT))
            .password(passwordEncoder.encode(password))
            .image(imageUrl)
            .speciality(speciality)
            .degree(degree)
            .experience(experience)
            .about(about)
            .available(true)
            .fees(parseDouble(fees, 0D))
            .addressJson(jsonUtil.toJson(parsedAddress == null ? Map.of() : parsedAddress))
            .slotsBookedJson(jsonUtil.toJson(Map.of()))
            .date(System.currentTimeMillis())
            .build();

        doctorRepository.save(doctor);

        Map<String, Object> response = ApiResponse.success();
        response.put("message", "Doctor Added");
        return response;
    }

    @GetMapping("/all-doctors")
    public Map<String, Object> allDoctors(HttpServletRequest request) {
        authService.requireAdmin(request);
        List<Map<String, Object>> doctors = doctorRepository.findAll().stream().map(doctor -> mapperService.doctor(doctor, false)).toList();
        Map<String, Object> response = ApiResponse.success();
        response.put("doctors", doctors);
        return response;
    }

    @PostMapping("/change-availability")
    public Map<String, Object> changeAvailability(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        authService.requireAdmin(request);
        String docId = RequestUtil.str(body, "docId");
        DoctorEntity doctor = doctorRepository.findById(docId).orElseThrow(() -> new IllegalArgumentException("Doctor not found"));
        doctor.setAvailable(!Boolean.TRUE.equals(doctor.getAvailable()));
        doctorRepository.save(doctor);

        Map<String, Object> response = ApiResponse.success();
        response.put("message", "Availablity Changed");
        return response;
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard(HttpServletRequest request) {
        authService.requireAdmin(request);

        List<DoctorEntity> doctors = doctorRepository.findAll();
        List<UserEntity> users = userRepository.findAll();
        List<AppointmentEntity> appointments = appointmentRepository.findAll();
        List<PharmacyEntity> pharmacies = pharmacyRepository.findAll();
        List<PharmacyOrderEntity> orders = pharmacyOrderRepository.findAll();

        long pendingPharmacies = pharmacies.stream().filter(pharmacy -> !Boolean.TRUE.equals(pharmacy.getIsApproved())).count();

        Map<String, Object> dashData = new HashMap<>();
        dashData.put("doctors", doctors.size());
        dashData.put("appointments", appointments.size());
        dashData.put("patients", users.size());
        dashData.put("pharmacies", pharmacies.size());
        dashData.put("pendingPharmacies", pendingPharmacies);
        dashData.put("pharmacyOrders", orders.size());
        dashData.put("latestAppointments", appointments.stream().map(mapperService::appointment).toList());
        dashData.put("latestPharmacyOrders", orders.stream().map(mapperService::pharmacyOrder).limit(10).toList());

        Map<String, Object> response = ApiResponse.success();
        response.put("dashData", dashData);
        return response;
    }

    @GetMapping("/pharmacies")
    public Map<String, Object> pharmacies(HttpServletRequest request, @RequestParam(value = "status", required = false) String status) {
        authService.requireAdmin(request);

        List<PharmacyEntity> list;
        if ("pending".equals(status)) {
            list = pharmacyRepository.findAll().stream().filter(pharmacy -> !Boolean.TRUE.equals(pharmacy.getIsApproved())).toList();
        } else if ("approved".equals(status)) {
            list = pharmacyRepository.findAll().stream().filter(pharmacy -> Boolean.TRUE.equals(pharmacy.getIsApproved())).toList();
        } else {
            list = pharmacyRepository.findAll();
        }

        List<Map<String, Object>> pharmacies = list.stream().map(pharmacy -> mapperService.pharmacy(pharmacy, true)).toList();

        Map<String, Object> response = ApiResponse.success();
        response.put("pharmacies", pharmacies);
        return response;
    }

    @PostMapping("/pharmacies/review")
    public Map<String, Object> reviewPharmacy(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        authService.requireAdmin(request);

        String pharmacyId = RequestUtil.str(body, "pharmacyId");
        Boolean approve = RequestUtil.bool(body, "approve");
        String notes = RequestUtil.str(body, "notes");

        PharmacyEntity pharmacy = pharmacyRepository.findById(pharmacyId).orElse(null);
        if (pharmacy == null) {
            return ApiResponse.failure("Pharmacy not found");
        }

        pharmacy.setIsApproved(Boolean.TRUE.equals(approve));
        pharmacy.setModerationNotes(notes);
        pharmacy.setApprovedAt(Boolean.TRUE.equals(approve) ? Instant.now() : null);
        pharmacy.setIsActive(Boolean.TRUE.equals(approve));
        pharmacy.setUpdatedAt(Instant.now());

        pharmacyRepository.save(pharmacy);

        notificationService.notifyPharmacist(
            pharmacyId,
            Boolean.TRUE.equals(approve) ? "Pharmacy approved" : "Pharmacy rejected",
            Boolean.TRUE.equals(approve) ? "Your pharmacy account has been approved by admin." : "Your pharmacy application requires changes: " + notes,
            Map.of("approval", Boolean.TRUE.equals(approve))
        );

        Map<String, Object> response = ApiResponse.success();
        response.put("message", Boolean.TRUE.equals(approve) ? "Pharmacy approved" : "Pharmacy rejected");
        response.put("pharmacy", mapperService.pharmacy(pharmacy, true));
        return response;
    }

    @PostMapping("/pharmacies/toggle-active")
    public Map<String, Object> togglePharmacy(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        authService.requireAdmin(request);

        String pharmacyId = RequestUtil.str(body, "pharmacyId");
        Boolean isActive = RequestUtil.bool(body, "isActive");

        PharmacyEntity pharmacy = pharmacyRepository.findById(pharmacyId).orElse(null);
        if (pharmacy == null) {
            return ApiResponse.failure("Pharmacy not found");
        }

        pharmacy.setIsActive(Boolean.TRUE.equals(isActive));
        pharmacy.setUpdatedAt(Instant.now());
        pharmacyRepository.save(pharmacy);

        notificationService.notifyPharmacist(
            pharmacyId,
            "Account status updated",
            Boolean.TRUE.equals(isActive) ? "Your pharmacy account has been reactivated." : "Your pharmacy account has been disabled by admin.",
            Map.of("isActive", Boolean.TRUE.equals(isActive))
        );

        Map<String, Object> response = ApiResponse.success();
        response.put("message", "Pharmacy status updated");
        response.put("pharmacy", mapperService.pharmacy(pharmacy, true));
        return response;
    }

    @GetMapping("/pharmacy-orders")
    public Map<String, Object> pharmacyOrders(
        HttpServletRequest request,
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "pharmacyId", required = false) String pharmacyId,
        @RequestParam(value = "userId", required = false) String userId
    ) {
        authService.requireAdmin(request);

        List<PharmacyOrderEntity> orders = pharmacyOrderRepository.findAll().stream()
            .filter(order -> isBlank(status) || status.equals(order.getStatus()))
            .filter(order -> isBlank(pharmacyId) || pharmacyId.equals(order.getPharmacyId()))
            .filter(order -> isBlank(userId) || userId.equals(order.getUserId()))
            .toList();

        Map<String, Object> response = ApiResponse.success();
        response.put("orders", orders.stream().map(mapperService::pharmacyOrder).toList());
        return response;
    }

    @PostMapping("/pharmacy-orders/update-status")
    public Map<String, Object> updatePharmacyOrderStatus(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        authService.requireAdmin(request);

        String orderId = RequestUtil.str(body, "orderId");
        String status = RequestUtil.str(body, "status");
        String note = RequestUtil.str(body, "note");

        PharmacyOrderEntity order = pharmacyOrderRepository.findById(orderId).orElse(null);
        if (order == null) {
            return ApiResponse.failure("Order not found");
        }

        order.setStatus(status);
        List<Object> timeline = new ArrayList<>(jsonUtil.toList(order.getStatusHistoryJson()));
        Map<String, Object> timelineEntry = new HashMap<>();
        timelineEntry.put("status", status);
        timelineEntry.put("note", note);
        timelineEntry.put("updatedBy", "admin");
        timelineEntry.put("updatedById", "admin");
        timelineEntry.put("timestamp", Instant.now().toString());
        timeline.add(timelineEntry);
        order.setStatusHistoryJson(jsonUtil.toJson(timeline));
        order.setUpdatedAt(Instant.now());

        pharmacyOrderRepository.save(order);

        notificationService.notifyPatient(order.getUserId(), "Order status updated", "Your pharmacy order has been updated to " + status + " by admin.", Map.of("orderId", order.getId(), "status", status));
        notificationService.notifyPharmacist(order.getPharmacyId(), "Order status updated", "Order " + order.getOrderNumber() + " has been updated to " + status + " by admin.", Map.of("orderId", order.getId(), "status", status));

        Map<String, Object> response = ApiResponse.success();
        response.put("message", "Order status updated");
        response.put("order", mapperService.pharmacyOrder(order));
        return response;
    }

    @GetMapping("/settings")
    public Map<String, Object> getSettings(HttpServletRequest request) {
        authService.requireAdmin(request);

        SettingEntity setting = settingRepository.findById("platform").orElse(null);
        if (setting == null) {
            setting = SettingEntity.builder()
                .key("platform")
                .valueJson(jsonUtil.toJson(DEFAULT_PLATFORM_SETTINGS))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
            settingRepository.save(setting);
        }

        Map<String, Object> merged = new HashMap<>(DEFAULT_PLATFORM_SETTINGS);
        Object value = jsonUtil.toObject(setting.getValueJson());
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                merged.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }

        Map<String, Object> response = ApiResponse.success();
        response.put("settings", merged);
        return response;
    }

    @PostMapping("/settings")
    public Map<String, Object> updateSettings(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        authService.requireAdmin(request);

        Integer consultationFee = parseInt(body.get("consultationFee"), 499);
        Integer cancellationWindowHours = parseInt(body.get("cancellationWindowHours"), 12);
        String videoProvider = RequestUtil.str(body, "videoProvider");
        String emergencyContact = RequestUtil.str(body, "emergencyContact");
        Integer allowedDeliveryRadiusKm = parseInt(body.get("allowedDeliveryRadiusKm"), 15);

        Map<String, Object> security = RequestUtil.map(body, "security");
        Boolean enforceMfa = parseBoolean(security.getOrDefault("enforceMfa", body.get("enforceMfa")), false);
        Integer sessionTimeoutMinutes = parseInt(security.getOrDefault("sessionTimeoutMinutes", body.get("sessionTimeoutMinutes")), 60);

        Map<String, Object> payload = new HashMap<>();
        payload.put("consultationFee", consultationFee);
        payload.put("cancellationWindowHours", cancellationWindowHours);
        payload.put("videoProvider", isBlank(videoProvider) ? "jitsi" : videoProvider);
        payload.put("emergencyContact", emergencyContact == null ? "" : emergencyContact);
        payload.put("allowedDeliveryRadiusKm", allowedDeliveryRadiusKm);
        payload.put("security", Map.of("enforceMfa", enforceMfa, "sessionTimeoutMinutes", sessionTimeoutMinutes));

        SettingEntity setting = settingRepository.findById("platform").orElse(SettingEntity.builder().key("platform").createdAt(Instant.now()).build());
        setting.setValueJson(jsonUtil.toJson(payload));
        setting.setUpdatedAt(Instant.now());
        settingRepository.save(setting);

        Map<String, Object> response = ApiResponse.success();
        response.put("message", "Settings updated");
        response.put("settings", payload);
        return response;
    }

    @GetMapping("/db/tables")
    public Map<String, Object> databaseTables(HttpServletRequest request) {
        authService.requireAdmin(request);

        String databaseName = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        List<Map<String, Object>> tables = jdbcTemplate.query(
            "SELECT table_name, table_rows FROM information_schema.tables " +
                "WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE' " +
                "ORDER BY table_name",
            (rs, rowNum) -> {
                Map<String, Object> item = new HashMap<>();
                item.put("name", rs.getString("table_name"));
                item.put("rowCount", rs.getLong("table_rows"));
                return item;
            }
        );

        Map<String, Object> response = ApiResponse.success();
        response.put("database", databaseName);
        response.put("serverVersion", resolveDatabaseVersion());
        response.put("tables", tables);
        return response;
    }

    @GetMapping("/db/rows")
    public Map<String, Object> databaseRows(
        HttpServletRequest request,
        @RequestParam("table") String table,
        @RequestParam(value = "page", defaultValue = "1") Integer page,
        @RequestParam(value = "limit", defaultValue = "25") Integer limit
    ) {
        authService.requireAdmin(request);

        if (isBlank(table)) {
            return ApiResponse.failure("Table is required");
        }

        int safePage = Math.max(page == null ? 1 : page, 1);
        int safeLimit = limit == null ? 25 : Math.min(Math.max(limit, 1), 100);

        Set<String> allowedTables = new HashSet<>(jdbcTemplate.queryForList(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'",
            String.class
        ));

        if (!allowedTables.contains(table)) {
            return ApiResponse.failure("Invalid table");
        }

        String safeTable = escapeIdentifier(table);
        long totalRows = parseLongObject(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM `" + safeTable + "`", Object.class), 0L);
        int offset = (safePage - 1) * safeLimit;

        List<String> columns = jdbcTemplate.queryForList(
            "SELECT column_name FROM information_schema.columns " +
                "WHERE table_schema = DATABASE() AND table_name = ? " +
                "ORDER BY ordinal_position",
            String.class,
            table
        );

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT * FROM `" + safeTable + "` LIMIT ? OFFSET ?",
            safeLimit,
            offset
        ).stream().map(this::normalizeRowValues).toList();

        int totalPages = totalRows <= 0 ? 1 : (int) Math.ceil(totalRows / (double) safeLimit);

        Map<String, Object> response = ApiResponse.success();
        response.put("database", jdbcTemplate.queryForObject("SELECT DATABASE()", String.class));
        response.put("table", table);
        response.put("columns", columns);
        response.put("rows", rows);
        response.put("pagination", Map.of(
            "page", safePage,
            "limit", safeLimit,
            "totalRows", totalRows,
            "totalPages", totalPages
        ));
        return response;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private double parseDouble(String value, double fallback) {
        try {
            return Double.parseDouble(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private Integer parseInt(Object value, Integer fallback) {
        if (value == null) return fallback;
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private Boolean parseBoolean(Object value, Boolean fallback) {
        if (value == null) return fallback;
        if (value instanceof Boolean bool) return bool;
        return "true".equalsIgnoreCase(String.valueOf(value)) || "1".equals(String.valueOf(value));
    }

    private Long parseLongObject(Object value, Long fallback) {
        if (value == null) return fallback;
        if (value instanceof Number number) return number.longValue();
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private Map<String, Object> normalizeRowValues(Map<String, Object> row) {
        Map<String, Object> normalized = new HashMap<>();
        row.forEach((key, value) -> normalized.put(key, normalizeJdbcValue(value)));
        return normalized;
    }

    private Object normalizeJdbcValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof byte[] bytes) {
            if (bytes.length == 1) {
                return bytes[0] != 0;
            }
            return Base64.getEncoder().encodeToString(bytes);
        }
        return value;
    }

    private String escapeIdentifier(String identifier) {
        return identifier.replace("`", "``");
    }

    private String resolveDatabaseVersion() {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            return metaData.getDatabaseProductName() + " " + metaData.getDatabaseProductVersion();
        } catch (SQLException ex) {
            return "unknown";
        }
    }

    private String normalizeConfigValue(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() >= 2) {
            if ((normalized.startsWith("\"") && normalized.endsWith("\"")) || (normalized.startsWith("'") && normalized.endsWith("'"))) {
                normalized = normalized.substring(1, normalized.length() - 1);
            }
        }
        return normalized;
    }
}
