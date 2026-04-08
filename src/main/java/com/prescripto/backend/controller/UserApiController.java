package com.prescripto.backend.controller;

import com.prescripto.backend.model.AppointmentEntity;
import com.prescripto.backend.model.DoctorEntity;
import com.prescripto.backend.model.PharmacyEntity;
import com.prescripto.backend.model.PharmacyOrderEntity;
import com.prescripto.backend.model.PrescriptionEntity;
import com.prescripto.backend.model.UserEntity;
import com.prescripto.backend.repository.AppointmentRepository;
import com.prescripto.backend.repository.DoctorRepository;
import com.prescripto.backend.repository.PharmacyOrderRepository;
import com.prescripto.backend.repository.PharmacyRepository;
import com.prescripto.backend.repository.PrescriptionRepository;
import com.prescripto.backend.repository.UserRepository;
import com.prescripto.backend.service.AuthService;
import com.prescripto.backend.service.FileUploadService;
import com.prescripto.backend.service.JwtService;
import com.prescripto.backend.service.MapperService;
import com.prescripto.backend.service.NotificationService;
import com.prescripto.backend.service.PaymentService;
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
import org.springframework.beans.factory.annotation.Value;
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
@RequestMapping("/api/user")
public class UserApiController {

    private final UserRepository userRepository;
    private final DoctorRepository doctorRepository;
    private final AppointmentRepository appointmentRepository;
    private final PharmacyRepository pharmacyRepository;
    private final PharmacyOrderRepository pharmacyOrderRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final AuthService authService;
    private final JwtService jwtService;
    private final MapperService mapperService;
    private final JsonUtil jsonUtil;
    private final PasswordEncoder passwordEncoder;
    private final PaymentService paymentService;
    private final NotificationService notificationService;
    private final FileUploadService fileUploadService;

    @Value("${app.currency:INR}")
    private String currency;

    public UserApiController(
        UserRepository userRepository,
        DoctorRepository doctorRepository,
        AppointmentRepository appointmentRepository,
        PharmacyRepository pharmacyRepository,
        PharmacyOrderRepository pharmacyOrderRepository,
        PrescriptionRepository prescriptionRepository,
        AuthService authService,
        JwtService jwtService,
        MapperService mapperService,
        JsonUtil jsonUtil,
        PasswordEncoder passwordEncoder,
        PaymentService paymentService,
        NotificationService notificationService,
        FileUploadService fileUploadService
    ) {
        this.userRepository = userRepository;
        this.doctorRepository = doctorRepository;
        this.appointmentRepository = appointmentRepository;
        this.pharmacyRepository = pharmacyRepository;
        this.pharmacyOrderRepository = pharmacyOrderRepository;
        this.prescriptionRepository = prescriptionRepository;
        this.authService = authService;
        this.jwtService = jwtService;
        this.mapperService = mapperService;
        this.jsonUtil = jsonUtil;
        this.passwordEncoder = passwordEncoder;
        this.paymentService = paymentService;
        this.notificationService = notificationService;
        this.fileUploadService = fileUploadService;
    }

    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody Map<String, Object> body) {
        String name = trim(RequestUtil.str(body, "name"));
        String email = trim(RequestUtil.str(body, "email"));
        String password = RequestUtil.str(body, "password");

        if (isBlank(name) || isBlank(email) || isBlank(password)) {
            return ApiResponse.failure("Missing Details");
        }

        if (!email.contains("@")) {
            return ApiResponse.failure("Please enter a valid email");
        }

        String normalizedEmail = email.toLowerCase(Locale.ROOT);
        if (userRepository.findByEmail(normalizedEmail).isPresent()) {
            return ApiResponse.failure("Email already registered. Please log in.");
        }

        if (password.length() < 8) {
            return ApiResponse.failure("Please enter a strong password");
        }

        UserEntity user = UserEntity.builder()
            .id(IdUtil.objectId())
            .name(name)
            .email(normalizedEmail)
            .password(passwordEncoder.encode(password))
            .image("data:image/png;base64,")
            .phone("000000000")
            .addressJson(jsonUtil.toJson(Map.of("line1", "", "line2", "")))
            .deliveryAddressesJson(jsonUtil.toJson(new ArrayList<>()))
            .defaultPharmacyId(null)
            .gender("Not Selected")
            .dob("Not Selected")
            .build();

        userRepository.save(user);

        Map<String, Object> response = ApiResponse.success();
        response.put("token", jwtService.generateIdToken(user.getId()));
        return response;
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, Object> body) {
        String email = trim(RequestUtil.str(body, "email"));
        String password = RequestUtil.str(body, "password");

        Optional<UserEntity> userOptional = userRepository.findByEmail(email == null ? "" : email.toLowerCase(Locale.ROOT));
        if (userOptional.isEmpty()) {
            return ApiResponse.failure("User does not exist");
        }

        UserEntity user = userOptional.get();
        if (!passwordEncoder.matches(password == null ? "" : password, user.getPassword())) {
            return ApiResponse.failure("Invalid credentials");
        }

        Map<String, Object> response = ApiResponse.success();
        response.put("token", jwtService.generateIdToken(user.getId()));
        return response;
    }

    @GetMapping("/get-profile")
    public Map<String, Object> getProfile(HttpServletRequest request) {
        String userId = authService.requireUserId(request);
        UserEntity user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));

        Map<String, Object> response = ApiResponse.success();
        response.put("userData", mapperService.user(user, false));
        return response;
    }

    @PostMapping("/update-profile")
    public Map<String, Object> updateProfile(
        HttpServletRequest request,
        @RequestParam Map<String, String> form,
        @RequestPart(value = "image", required = false) MultipartFile image
    ) {
        String userId = authService.requireUserId(request);
        UserEntity user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));

        String name = trim(form.get("name"));
        String phone = trim(form.get("phone"));
        String dob = trim(form.get("dob"));
        String gender = trim(form.get("gender"));
        String address = form.get("address");

        if (isBlank(name) || isBlank(phone) || isBlank(dob) || isBlank(gender)) {
            return ApiResponse.failure("Data Missing");
        }

        user.setName(name);
        user.setPhone(phone);
        user.setDob(dob);
        user.setGender(gender);
        if (!isBlank(address)) {
            Object parsedAddress = parseJsonMaybe(address);
            user.setAddressJson(jsonUtil.toJson(parsedAddress));
        }

        String imageUrl = fileUploadService.uploadImageOrFallback(image);
        if (!isBlank(imageUrl)) {
            user.setImage(imageUrl);
        }

        userRepository.save(user);
        Map<String, Object> response = ApiResponse.success();
        response.put("message", "Profile Updated");
        return response;
    }

    @PostMapping("/book-appointment")
    public Map<String, Object> bookAppointment(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        String userId = authService.requireUserId(request);
        String docId = RequestUtil.str(body, "docId");
        String slotDate = RequestUtil.str(body, "slotDate");
        String slotTime = RequestUtil.str(body, "slotTime");

        DoctorEntity doctor = doctorRepository.findById(docId).orElse(null);
        if (doctor == null) {
            return ApiResponse.failure("Doctor Not Available");
        }
        if (!Boolean.TRUE.equals(doctor.getAvailable())) {
            return ApiResponse.failure("Doctor Not Available");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> slotsBooked = (Map<String, Object>) (Map<?, ?>) jsonUtil.toMap(doctor.getSlotsBookedJson());
        Object existingSlotsValue = slotsBooked.get(slotDate);

        List<String> slots = new ArrayList<>();
        if (existingSlotsValue instanceof List<?> list) {
            for (Object value : list) {
                slots.add(String.valueOf(value));
            }
        }

        if (slots.contains(slotTime)) {
            return ApiResponse.failure("Slot Not Available");
        }

        slots.add(slotTime);
        slotsBooked.put(slotDate, slots);

        UserEntity user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
        Map<String, Object> userData = mapperService.user(user, false);
        Map<String, Object> doctorData = mapperService.doctor(doctor, false);
        doctorData.remove("slots_booked");

        AppointmentEntity appointment = AppointmentEntity.builder()
            .id(IdUtil.objectId())
            .userId(userId)
            .docId(docId)
            .slotDate(slotDate)
            .slotTime(slotTime)
            .userDataJson(jsonUtil.toJson(userData))
            .docDataJson(jsonUtil.toJson(doctorData))
            .amount(doctor.getFees())
            .date(System.currentTimeMillis())
            .cancelled(false)
            .payment(false)
            .isCompleted(false)
            .consultationJson(jsonUtil.toJson(new HashMap<>()))
            .build();

        appointmentRepository.save(appointment);
        doctor.setSlotsBookedJson(jsonUtil.toJson(slotsBooked));
        doctorRepository.save(doctor);

        Map<String, Object> response = ApiResponse.success();
        response.put("message", "Appointment Booked");
        return response;
    }

    @GetMapping("/appointments")
    public Map<String, Object> appointments(HttpServletRequest request) {
        String userId = authService.requireUserId(request);
        List<Map<String, Object>> mapped = appointmentRepository.findByUserId(userId)
            .stream()
            .map(mapperService::appointment)
            .toList();

        Map<String, Object> response = ApiResponse.success();
        response.put("appointments", mapped);
        return response;
    }

    @PostMapping("/cancel-appointment")
    public Map<String, Object> cancelAppointment(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        String userId = authService.requireUserId(request);
        String appointmentId = RequestUtil.str(body, "appointmentId");

        AppointmentEntity appointment = appointmentRepository.findById(appointmentId).orElseThrow(() -> new IllegalArgumentException("Appointment not found"));
        if (!userId.equals(appointment.getUserId())) {
            return ApiResponse.failure("Unauthorized action");
        }

        appointment.setCancelled(true);
        appointmentRepository.save(appointment);

        DoctorEntity doctor = doctorRepository.findById(appointment.getDocId()).orElse(null);
        if (doctor != null) {
            Map<String, Object> slotsBooked = jsonUtil.toMap(doctor.getSlotsBookedJson());
            Object slotsObj = slotsBooked.get(appointment.getSlotDate());
            if (slotsObj instanceof List<?> list) {
                List<String> filtered = new ArrayList<>();
                for (Object value : list) {
                    String slot = String.valueOf(value);
                    if (!slot.equals(appointment.getSlotTime())) {
                        filtered.add(slot);
                    }
                }
                slotsBooked.put(appointment.getSlotDate(), filtered);
                doctor.setSlotsBookedJson(jsonUtil.toJson(slotsBooked));
                doctorRepository.save(doctor);
            }
        }

        Map<String, Object> response = ApiResponse.success();
        response.put("message", "Appointment Cancelled");
        return response;
    }

    @PostMapping("/payment-razorpay")
    public Map<String, Object> paymentRazorpay(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        authService.requireUserId(request);
        String appointmentId = RequestUtil.str(body, "appointmentId");

        AppointmentEntity appointment = appointmentRepository.findById(appointmentId).orElse(null);
        if (appointment == null || Boolean.TRUE.equals(appointment.getCancelled())) {
            return ApiResponse.failure("Appointment Cancelled or not found");
        }

        long amount = Math.round((appointment.getAmount() == null ? 0D : appointment.getAmount()) * 100);
        Map<String, Object> order = paymentService.createRazorpayOrder(amount, currency, appointmentId);

        Map<String, Object> response = ApiResponse.success();
        response.put("order", order);
        return response;
    }

    @PostMapping("/verifyRazorpay")
    public Map<String, Object> verifyRazorpay(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        authService.requireUserId(request);
        String razorpayOrderId = RequestUtil.str(body, "razorpay_order_id");

        Map<String, Object> orderInfo = paymentService.fetchRazorpayOrder(razorpayOrderId);
        String status = String.valueOf(orderInfo.getOrDefault("status", ""));

        if ("paid".equalsIgnoreCase(status)) {
            String receipt = String.valueOf(orderInfo.getOrDefault("receipt", ""));
            appointmentRepository.findById(receipt).ifPresent(appointment -> {
                appointment.setPayment(true);
                appointmentRepository.save(appointment);
            });
            Map<String, Object> response = ApiResponse.success();
            response.put("message", "Payment Successful");
            return response;
        }

        return ApiResponse.failure("Payment Failed");
    }

    @PostMapping("/payment-stripe")
    public Map<String, Object> paymentStripe(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        authService.requireUserId(request);
        String appointmentId = RequestUtil.str(body, "appointmentId");

        AppointmentEntity appointment = appointmentRepository.findById(appointmentId).orElse(null);
        if (appointment == null || Boolean.TRUE.equals(appointment.getCancelled())) {
            return ApiResponse.failure("Appointment Cancelled or not found");
        }

        String origin = request.getHeader("origin");
        long amount = Math.round((appointment.getAmount() == null ? 0D : appointment.getAmount()) * 100);
        String sessionUrl = paymentService.createStripeCheckoutSession(origin, appointmentId, amount, currency);

        Map<String, Object> response = ApiResponse.success();
        response.put("session_url", sessionUrl);
        return response;
    }

    @PostMapping("/verifyStripe")
    public Map<String, Object> verifyStripe(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        authService.requireUserId(request);
        String appointmentId = RequestUtil.str(body, "appointmentId");
        String success = RequestUtil.str(body, "success");

        if ("true".equals(success)) {
            appointmentRepository.findById(appointmentId).ifPresent(appointment -> {
                appointment.setPayment(true);
                appointmentRepository.save(appointment);
            });
            Map<String, Object> response = ApiResponse.success();
            response.put("message", "Payment Successful");
            return response;
        }

        return ApiResponse.failure("Payment Failed");
    }

    @GetMapping("/pharmacies")
    public Map<String, Object> listPharmacies(HttpServletRequest request) {
        authService.requireUserId(request);
        List<Map<String, Object>> pharmacies = pharmacyRepository.findByIsApprovedAndIsActive(true, true)
            .stream()
            .map(entity -> mapperService.pharmacy(entity, false))
            .toList();

        Map<String, Object> response = ApiResponse.success();
        response.put("pharmacies", pharmacies);
        return response;
    }

    @PostMapping("/order-medicine")
    public Map<String, Object> orderMedicine(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        String userId = authService.requireUserId(request);
        String appointmentId = RequestUtil.str(body, "appointmentId");
        String pharmacyId = RequestUtil.str(body, "pharmacyId");
        Object logistics = body.get("logistics");
        String notesForPharmacist = RequestUtil.str(body, "notesForPharmacist");

        AppointmentEntity appointment = appointmentRepository.findById(appointmentId).orElse(null);
        if (appointment == null || !userId.equals(appointment.getUserId())) {
            return ApiResponse.failure("Appointment not found");
        }

        if (isBlank(appointment.getPrescriptionId())) {
            return ApiResponse.failure("Prescription not available for this appointment");
        }

        if (!isBlank(appointment.getPharmacyOrderId())) {
            Optional<PharmacyOrderEntity> existing = pharmacyOrderRepository.findById(appointment.getPharmacyOrderId());
            if (existing.isPresent()) {
                String status = existing.get().getStatus();
                if (!List.of("completed", "cancelled", "rejected").contains(status)) {
                    return ApiResponse.failure("Order already in progress for this prescription");
                }
            }
        }

        PharmacyEntity pharmacy = pharmacyRepository.findById(pharmacyId).orElse(null);
        if (pharmacy == null || !Boolean.TRUE.equals(pharmacy.getIsApproved()) || !Boolean.TRUE.equals(pharmacy.getIsActive())) {
            return ApiResponse.failure("Selected pharmacy is not available");
        }

        PrescriptionEntity prescription = prescriptionRepository.findById(appointment.getPrescriptionId()).orElse(null);
        if (prescription == null) {
            return ApiResponse.failure("Prescription data missing");
        }

        UserEntity user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));

        Object logisticsPayload = logistics;
        if (logistics instanceof String logisticsString && !logisticsString.isBlank()) {
            logisticsPayload = parseJsonMaybe(logisticsString);
        }
        if (logisticsPayload == null) {
            logisticsPayload = Map.of("method", "pickup");
        }

        String orderId = IdUtil.objectId();
        Instant now = Instant.now();

        List<Map<String, Object>> statusHistory = new ArrayList<>();
        statusHistory.add(Map.of(
            "status", "pending",
            "note", "Order created",
            "updatedBy", "patient",
            "updatedById", userId,
            "timestamp", now.toString()
        ));

        Map<String, Object> patientSnapshot = new HashMap<>();
        patientSnapshot.put("name", user.getName());
        patientSnapshot.put("phone", user.getPhone());
        patientSnapshot.put("address", jsonUtil.toObject(user.getAddressJson()));

        PharmacyOrderEntity order = PharmacyOrderEntity.builder()
            .id(orderId)
            .orderNumber(generateOrderNumber())
            .prescriptionId(prescription.getId())
            .appointmentId(appointment.getId())
            .userId(userId)
            .pharmacyId(pharmacyId)
            .status("pending")
            .statusHistoryJson(jsonUtil.toJson(statusHistory))
            .logisticsJson(jsonUtil.toJson(logisticsPayload))
            .notesForPatient("")
            .notesForInternal(notesForPharmacist)
            .prescriptionSnapshotJson(jsonUtil.toJson(mapperService.prescription(prescription)))
            .patientSnapshotJson(jsonUtil.toJson(patientSnapshot))
            .paymentStatus("pending")
            .createdVia("patient")
            .createdAt(now)
            .updatedAt(now)
            .build();

        pharmacyOrderRepository.save(order);
        appointment.setPharmacyOrderId(orderId);
        appointmentRepository.save(appointment);

        notificationService.notifyPharmacist(pharmacyId, "New medicine order", "You have received a new order " + order.getOrderNumber() + ".", Map.of("orderId", order.getId()));
        notificationService.notifyPatient(userId, "Order placed", "Your pharmacy order has been created. We will notify you about progress.", Map.of("orderId", order.getId()));

        Map<String, Object> response = ApiResponse.success();
        response.put("message", "Order created");
        response.put("order", mapperService.pharmacyOrder(order));
        return response;
    }

    @GetMapping("/pharmacy-orders")
    public Map<String, Object> pharmacyOrders(HttpServletRequest request) {
        String userId = authService.requireUserId(request);
        List<Map<String, Object>> orders = pharmacyOrderRepository.findByUserIdOrderByCreatedAtDesc(userId)
            .stream()
            .map(mapperService::pharmacyOrder)
            .toList();

        Map<String, Object> response = ApiResponse.success();
        response.put("orders", orders);
        return response;
    }

    @PostMapping("/pharmacy-orders/timeline")
    public Map<String, Object> pharmacyOrderTimeline(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        String userId = authService.requireUserId(request);
        String orderId = RequestUtil.str(body, "orderId");

        PharmacyOrderEntity order = pharmacyOrderRepository.findById(orderId).orElse(null);
        if (order == null || !userId.equals(order.getUserId())) {
            return ApiResponse.failure("Order not found");
        }

        Map<String, Object> response = ApiResponse.success();
        response.put("timeline", jsonUtil.toObject(order.getStatusHistoryJson()));
        response.put("status", order.getStatus());
        response.put("logistics", jsonUtil.toObject(order.getLogisticsJson()));
        return response;
    }

    @GetMapping("/prescriptions")
    public Map<String, Object> listPrescriptions(HttpServletRequest request) {
        String userId = authService.requireUserId(request);

        List<Map<String, Object>> prescriptions = prescriptionRepository.findByUserIdOrderByCreatedAtDesc(userId)
            .stream()
            .map(mapperService::prescription)
            .toList();

        Map<String, Object> response = ApiResponse.success();
        response.put("prescriptions", prescriptions);
        return response;
    }

    @PostMapping("/prescription/detail")
    public Map<String, Object> prescriptionDetail(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        String userId = authService.requireUserId(request);
        String appointmentId = RequestUtil.str(body, "appointmentId");

        AppointmentEntity appointment = appointmentRepository.findById(appointmentId).orElse(null);
        if (appointment == null || !userId.equals(appointment.getUserId())) {
            return ApiResponse.failure("Appointment not found");
        }

        PrescriptionEntity prescription = prescriptionRepository.findByAppointmentId(appointmentId).orElse(null);
        Map<String, Object> response = ApiResponse.success();
        response.put("prescription", prescription == null ? null : mapperService.prescription(prescription));
        return response;
    }

    private String generateOrderNumber() {
        long random = (long) (1000 + Math.floor(Math.random() * 9000));
        return "RX" + System.currentTimeMillis() + random;
    }

    private Object parseJsonMaybe(String value) {
        Object parsed = jsonUtil.toObject(value);
        return parsed == null ? value : parsed;
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
