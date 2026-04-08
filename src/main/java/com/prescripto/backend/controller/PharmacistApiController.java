package com.prescripto.backend.controller;

import com.prescripto.backend.model.AppointmentEntity;
import com.prescripto.backend.model.PharmacyEntity;
import com.prescripto.backend.model.PharmacyOrderEntity;
import com.prescripto.backend.model.PrescriptionEntity;
import com.prescripto.backend.model.UserEntity;
import com.prescripto.backend.repository.AppointmentRepository;
import com.prescripto.backend.repository.PharmacyOrderRepository;
import com.prescripto.backend.repository.PharmacyRepository;
import com.prescripto.backend.repository.PrescriptionRepository;
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
import java.time.Instant;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/pharmacist")
public class PharmacistApiController {

    private static final Map<String, List<String>> ALLOWED_STATUS_TRANSITIONS = Map.of(
        "pending", List.of("accepted", "rejected"),
        "accepted", List.of("processing", "rejected"),
        "processing", List.of("ready", "shipped"),
        "ready", List.of("completed", "shipped"),
        "shipped", List.of("completed")
    );

    private static final Map<String, Map<String, String>> STATUS_NOTIFICATION = Map.of(
        "accepted", Map.of("title", "Order accepted", "message", "Your pharmacy order has been accepted and will be prepared shortly."),
        "processing", Map.of("title", "Order is being prepared", "message", "Your medicines are currently being prepared."),
        "ready", Map.of("title", "Order ready for pickup", "message", "Your order is ready for pickup at the pharmacy."),
        "shipped", Map.of("title", "Order on the way", "message", "Your order has been shipped."),
        "completed", Map.of("title", "Order delivered", "message", "Your order has been completed."),
        "rejected", Map.of("title", "Order could not be fulfilled", "message", "The pharmacy could not fulfill your order. Please contact support.")
    );

    private final PharmacyRepository pharmacyRepository;
    private final PharmacyOrderRepository pharmacyOrderRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final AppointmentRepository appointmentRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthService authService;
    private final MapperService mapperService;
    private final FileUploadService fileUploadService;
    private final NotificationService notificationService;
    private final JsonUtil jsonUtil;

    public PharmacistApiController(
        PharmacyRepository pharmacyRepository,
        PharmacyOrderRepository pharmacyOrderRepository,
        PrescriptionRepository prescriptionRepository,
        AppointmentRepository appointmentRepository,
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        JwtService jwtService,
        AuthService authService,
        MapperService mapperService,
        FileUploadService fileUploadService,
        NotificationService notificationService,
        JsonUtil jsonUtil
    ) {
        this.pharmacyRepository = pharmacyRepository;
        this.pharmacyOrderRepository = pharmacyOrderRepository;
        this.prescriptionRepository = prescriptionRepository;
        this.appointmentRepository = appointmentRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authService = authService;
        this.mapperService = mapperService;
        this.fileUploadService = fileUploadService;
        this.notificationService = notificationService;
        this.jsonUtil = jsonUtil;
    }

    @PostMapping("/register")
    public Map<String, Object> register(
        @RequestParam Map<String, String> form,
        @RequestPart(value = "documents", required = false) List<MultipartFile> documents
    ) {
        String name = form.get("name");
        String email = form.get("email");
        String password = form.get("password");
        String ownerName = form.get("ownerName");
        String phone = form.get("phone");
        String alternatePhone = form.get("alternatePhone");
        String address = form.get("address");
        String licenseNumber = form.get("licenseNumber");
        String gstNumber = form.get("gstNumber");
        String deliveryOptions = form.get("deliveryOptions");
        String serviceRadiusKm = form.get("serviceRadiusKm");

        if (isBlank(name) || isBlank(email) || isBlank(password) || isBlank(ownerName) || isBlank(phone) || isBlank(address) || isBlank(licenseNumber)) {
            return ApiResponse.failure("Missing required fields");
        }

        if (pharmacyRepository.findByEmail(email.toLowerCase(Locale.ROOT)).isPresent()) {
            return ApiResponse.failure("Pharmacy already registered");
        }

        List<String> uploadedDocuments = documents == null
            ? List.of()
            : documents.stream().map(file -> fileUploadService.uploadAnyOrFallback(file, "prescripto/pharmacy-documents")).toList();

        Object parsedAddress = jsonUtil.toObject(address);
        Object parsedDeliveryOptions = jsonUtil.toObject(deliveryOptions);

        Instant now = Instant.now();
        PharmacyEntity pharmacy = PharmacyEntity.builder()
            .id(IdUtil.objectId())
            .name(name)
            .email(email.toLowerCase(Locale.ROOT))
            .password(passwordEncoder.encode(password))
            .ownerName(ownerName)
            .phone(phone)
            .alternatePhone(alternatePhone)
            .addressJson(jsonUtil.toJson(parsedAddress == null ? Map.of() : parsedAddress))
            .licenseNumber(licenseNumber)
            .licenseDocumentsJson(jsonUtil.toJson(uploadedDocuments))
            .gstNumber(gstNumber)
            .deliveryOptionsJson(jsonUtil.toJson(parsedDeliveryOptions == null ? List.of() : parsedDeliveryOptions))
            .serviceRadiusKm(parseDouble(serviceRadiusKm, null))
            .isApproved(false)
            .isActive(false)
            .createdAt(now)
            .updatedAt(now)
            .build();

        pharmacyRepository.save(pharmacy);

        Map<String, Object> response = ApiResponse.success();
        response.put("message", "Pharmacy submitted for approval");
        return response;
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, Object> body) {
        String email = RequestUtil.str(body, "email");
        String password = RequestUtil.str(body, "password");

        Optional<PharmacyEntity> pharmacyOptional = pharmacyRepository.findByEmail(email == null ? "" : email.toLowerCase(Locale.ROOT));
        if (pharmacyOptional.isEmpty()) {
            pharmacyOptional = pharmacyRepository.findByEmail(email);
        }

        if (pharmacyOptional.isEmpty()) {
            return ApiResponse.failure("Invalid credentials");
        }

        PharmacyEntity pharmacy = pharmacyOptional.get();
        if (!passwordEncoder.matches(password == null ? "" : password, pharmacy.getPassword())) {
            return ApiResponse.failure("Invalid credentials");
        }

        Map<String, Object> response = ApiResponse.success();
        response.put("token", jwtService.generateIdToken(pharmacy.getId()));
        response.put("requiresApproval", !Boolean.TRUE.equals(pharmacy.getIsApproved()));
        return response;
    }

    @GetMapping("/profile")
    public Map<String, Object> profile(HttpServletRequest request) {
        PharmacyEntity pharmacy = authService.requirePharmacist(request, pharmacyRepository);
        Map<String, Object> response = ApiResponse.success();
        response.put("pharmacy", mapperService.pharmacy(pharmacy, false));
        return response;
    }

    @PostMapping("/update-profile")
    public Map<String, Object> updateProfile(
        HttpServletRequest request,
        @RequestParam Map<String, String> form,
        @RequestPart(value = "documents", required = false) List<MultipartFile> documents
    ) {
        PharmacyEntity pharmacy = authService.requirePharmacist(request, pharmacyRepository);

        String name = form.get("name");
        String ownerName = form.get("ownerName");
        String phone = form.get("phone");
        String alternatePhone = form.get("alternatePhone");
        String address = form.get("address");
        String deliveryOptions = form.get("deliveryOptions");
        String serviceRadiusKm = form.get("serviceRadiusKm");
        String operatingHours = form.get("operatingHours");

        if (!isBlank(name)) pharmacy.setName(name);
        if (!isBlank(ownerName)) pharmacy.setOwnerName(ownerName);
        if (!isBlank(phone)) pharmacy.setPhone(phone);
        if (alternatePhone != null) pharmacy.setAlternatePhone(alternatePhone);
        if (!isBlank(address)) {
            Object parsedAddress = jsonUtil.toObject(address);
            pharmacy.setAddressJson(jsonUtil.toJson(parsedAddress == null ? address : parsedAddress));
        }
        if (!isBlank(deliveryOptions)) {
            Object parsedDelivery = jsonUtil.toObject(deliveryOptions);
            pharmacy.setDeliveryOptionsJson(jsonUtil.toJson(parsedDelivery == null ? deliveryOptions : parsedDelivery));
        }
        if (!isBlank(serviceRadiusKm)) {
            pharmacy.setServiceRadiusKm(parseDouble(serviceRadiusKm, pharmacy.getServiceRadiusKm()));
        }
        if (!isBlank(operatingHours)) {
            Object parsedHours = jsonUtil.toObject(operatingHours);
            pharmacy.setOperatingHoursJson(jsonUtil.toJson(parsedHours == null ? operatingHours : parsedHours));
        }

        if (documents != null && !documents.isEmpty()) {
            List<String> uploadedDocuments = documents.stream().map(file -> fileUploadService.uploadAnyOrFallback(file, "prescripto/pharmacy-documents")).toList();
            pharmacy.setLicenseDocumentsJson(jsonUtil.toJson(uploadedDocuments));
        }

        pharmacy.setUpdatedAt(Instant.now());
        pharmacyRepository.save(pharmacy);

        Map<String, Object> response = ApiResponse.success();
        response.put("message", "Profile updated");
        response.put("pharmacy", mapperService.pharmacy(pharmacy, false));
        return response;
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard(HttpServletRequest request) {
        PharmacyEntity pharmacy = authService.requirePharmacist(request, pharmacyRepository);

        List<PharmacyOrderEntity> orders = pharmacyOrderRepository.findByPharmacyIdOrderByCreatedAtDesc(pharmacy.getId());

        double revenue = 0D;
        int pending = 0;
        int inProgress = 0;
        int completed = 0;
        for (PharmacyOrderEntity order : orders) {
            String status = order.getStatus() == null ? "pending" : order.getStatus();
            if ("pending".equals(status)) {
                pending++;
            }
            if (List.of("accepted", "processing", "ready", "shipped").contains(status)) {
                inProgress++;
            }
            if ("completed".equals(status)) {
                completed++;
                revenue += order.getTotalAmount() == null ? 0D : order.getTotalAmount();
            }
        }

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalOrders", orders.size());
        stats.put("pending", pending);
        stats.put("inProgress", inProgress);
        stats.put("completed", completed);
        stats.put("revenue", revenue);
        stats.put("latestOrders", orders.stream().limit(5).map(mapperService::pharmacyOrder).toList());

        Map<String, Object> response = ApiResponse.success();
        response.put("stats", stats);
        return response;
    }

    @PostMapping("/orders")
    public Map<String, Object> orders(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        PharmacyEntity pharmacy = authService.requirePharmacist(request, pharmacyRepository);

        String status = RequestUtil.str(body, "status");
        String search = RequestUtil.str(body, "search");

        List<Map<String, Object>> orders = pharmacyOrderRepository.findByPharmacyIdOrderByCreatedAtDesc(pharmacy.getId())
            .stream()
            .filter(order -> isBlank(status) || status.equals(order.getStatus()))
            .filter(order -> isBlank(search) || containsIgnoreCase(order.getOrderNumber(), search))
            .map(mapperService::pharmacyOrder)
            .toList();

        Map<String, Object> response = ApiResponse.success();
        response.put("orders", orders);
        return response;
    }

    @PostMapping("/orders/detail")
    public Map<String, Object> orderDetail(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        PharmacyEntity pharmacy = authService.requirePharmacist(request, pharmacyRepository);
        String orderId = RequestUtil.str(body, "orderId");

        PharmacyOrderEntity order = pharmacyOrderRepository.findById(orderId).orElse(null);
        if (order == null || !pharmacy.getId().equals(order.getPharmacyId())) {
            return ApiResponse.failure("Order not found");
        }

        Map<String, Object> orderMap = mapperService.pharmacyOrder(order);

        PrescriptionEntity prescription = prescriptionRepository.findById(order.getPrescriptionId()).orElse(null);
        if (prescription != null) {
            orderMap.put("prescriptionId", mapperService.prescription(prescription));
        }

        UserEntity user = userRepository.findById(order.getUserId()).orElse(null);
        if (user != null) {
            Map<String, Object> userSummary = new HashMap<>();
            userSummary.put("_id", user.getId());
            userSummary.put("name", user.getName());
            userSummary.put("email", user.getEmail());
            userSummary.put("phone", user.getPhone());
            userSummary.put("address", jsonUtil.toObject(user.getAddressJson()));
            orderMap.put("userId", userSummary);
        }

        Map<String, Object> response = ApiResponse.success();
        response.put("order", orderMap);
        return response;
    }

    @PostMapping("/orders/update-status")
    public Map<String, Object> updateOrderStatus(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        PharmacyEntity pharmacy = authService.requirePharmacist(request, pharmacyRepository);

        String orderId = RequestUtil.str(body, "orderId");
        String status = RequestUtil.str(body, "status");
        String note = RequestUtil.str(body, "note");
        Map<String, Object> logistics = RequestUtil.map(body, "logistics");

        PharmacyOrderEntity order = pharmacyOrderRepository.findById(orderId).orElse(null);
        if (order == null || !pharmacy.getId().equals(order.getPharmacyId())) {
            return ApiResponse.failure("Order not found");
        }

        if (status == null || status.isBlank()) {
            return ApiResponse.failure("Status is required");
        }

        String currentStatus = order.getStatus() == null ? "pending" : order.getStatus();
        List<Object> statusHistory = new ArrayList<>(jsonUtil.toList(order.getStatusHistoryJson()));

        if (currentStatus.equals(status)) {
            if (!isBlank(note)) {
                order.setNotesForPatient(note);
                Map<String, Object> timelineEntry = new HashMap<>();
                timelineEntry.put("status", status);
                timelineEntry.put("note", note);
                timelineEntry.put("updatedBy", "pharmacist");
                timelineEntry.put("updatedById", pharmacy.getId());
                timelineEntry.put("timestamp", Instant.now().toString());
                statusHistory.add(timelineEntry);
                order.setStatusHistoryJson(jsonUtil.toJson(statusHistory));
                order.setUpdatedAt(Instant.now());
                pharmacyOrderRepository.save(order);

                sendStatusNotification(order, status, note);

                Map<String, Object> response = ApiResponse.success();
                response.put("message", "Patient note shared");
                response.put("order", mapperService.pharmacyOrder(order));
                return response;
            }

            Map<String, Object> response = ApiResponse.success();
            response.put("message", "Status already updated");
            response.put("order", mapperService.pharmacyOrder(order));
            return response;
        }

        List<String> allowed = ALLOWED_STATUS_TRANSITIONS.getOrDefault(currentStatus, List.of());
        if (!allowed.contains(status)) {
            return ApiResponse.failure("Cannot move order from " + currentStatus + " to " + status);
        }

        order.setStatus(status);
        Map<String, Object> timelineEntry = new HashMap<>();
        timelineEntry.put("status", status);
        timelineEntry.put("note", note);
        timelineEntry.put("updatedBy", "pharmacist");
        timelineEntry.put("updatedById", pharmacy.getId());
        timelineEntry.put("timestamp", Instant.now().toString());
        statusHistory.add(timelineEntry);

        if (!isBlank(note)) {
            order.setNotesForPatient(note);
        }

        if (logistics != null && !logistics.isEmpty()) {
            Map<String, Object> currentLogistics = jsonUtil.toMap(order.getLogisticsJson());
            currentLogistics.putAll(logistics);
            order.setLogisticsJson(jsonUtil.toJson(currentLogistics));
        }

        if ("completed".equals(status)) {
            Map<String, Object> currentLogistics = jsonUtil.toMap(order.getLogisticsJson());
            currentLogistics.put("deliveredAt", Instant.now().toString());
            order.setLogisticsJson(jsonUtil.toJson(currentLogistics));
        }

        order.setStatusHistoryJson(jsonUtil.toJson(statusHistory));
        order.setUpdatedAt(Instant.now());

        pharmacyOrderRepository.save(order);
        sendStatusNotification(order, status, note);

        Map<String, Object> response = ApiResponse.success();
        response.put("message", "Order status updated");
        response.put("order", mapperService.pharmacyOrder(order));
        return response;
    }

    @PostMapping("/orders/timeline")
    public Map<String, Object> timeline(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        PharmacyEntity pharmacy = authService.requirePharmacist(request, pharmacyRepository);
        String orderId = RequestUtil.str(body, "orderId");

        PharmacyOrderEntity order = pharmacyOrderRepository.findById(orderId).orElse(null);
        if (order == null || !pharmacy.getId().equals(order.getPharmacyId())) {
            return ApiResponse.failure("Order not found");
        }

        Map<String, Object> response = ApiResponse.success();
        response.put("timeline", jsonUtil.toList(order.getStatusHistoryJson()));
        response.put("status", order.getStatus());
        return response;
    }

    @PostMapping("/orders/prescription")
    public Map<String, Object> getOrderPrescription(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        PharmacyEntity pharmacy = authService.requirePharmacist(request, pharmacyRepository);
        String appointmentId = RequestUtil.str(body, "appointmentId");

        AppointmentEntity appointment = appointmentRepository.findById(appointmentId).orElse(null);
        if (appointment == null) {
            return ApiResponse.failure("Appointment not found");
        }

        if (!isBlank(appointment.getPharmacyOrderId())) {
            PharmacyOrderEntity order = pharmacyOrderRepository.findById(appointment.getPharmacyOrderId()).orElse(null);
            if (order == null || !pharmacy.getId().equals(order.getPharmacyId())) {
                return ApiResponse.failure("Unauthorized");
            }
        }

        PrescriptionEntity prescription = prescriptionRepository.findByAppointmentId(appointmentId).orElse(null);

        Map<String, Object> response = ApiResponse.success();
        response.put("prescription", prescription == null ? null : mapperService.prescription(prescription));
        return response;
    }

    private void sendStatusNotification(PharmacyOrderEntity order, String status, String note) {
        Map<String, String> payload = STATUS_NOTIFICATION.get(status);
        if (payload == null) {
            return;
        }

        String message = payload.get("message");
        if (!isBlank(note)) {
            message = (message + " " + note).trim();
        }

        notificationService.notifyPatient(
            order.getUserId(),
            payload.get("title"),
            message,
            Map.of("orderId", order.getId(), "status", status)
        );
    }

    private boolean containsIgnoreCase(String value, String query) {
        if (value == null || query == null) return false;
        return value.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
    }

    private Double parseDouble(String value, Double fallback) {
        if (isBlank(value)) return fallback;
        try {
            return Double.parseDouble(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
