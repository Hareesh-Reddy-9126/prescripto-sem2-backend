package com.prescripto.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prescripto.backend.model.AppointmentEntity;
import com.prescripto.backend.model.DoctorEntity;
import com.prescripto.backend.model.DoctorRequestEntity;
import com.prescripto.backend.model.LabReportEntity;
import com.prescripto.backend.model.NotificationEntity;
import com.prescripto.backend.model.PharmacyEntity;
import com.prescripto.backend.model.PharmacyOrderEntity;
import com.prescripto.backend.model.PrescriptionEntity;
import com.prescripto.backend.model.SettingEntity;
import com.prescripto.backend.model.UserEntity;
import com.prescripto.backend.repository.AppointmentRepository;
import com.prescripto.backend.repository.DoctorRepository;
import com.prescripto.backend.repository.DoctorRequestRepository;
import com.prescripto.backend.repository.LabReportRepository;
import com.prescripto.backend.repository.NotificationRepository;
import com.prescripto.backend.repository.PharmacyOrderRepository;
import com.prescripto.backend.repository.PharmacyRepository;
import com.prescripto.backend.repository.PrescriptionRepository;
import com.prescripto.backend.repository.SettingRepository;
import com.prescripto.backend.repository.UserRepository;
import com.prescripto.backend.util.JsonUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

@Service
public class MongoBackupImportService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MongoBackupImportService.class);
    private static final String IMPORT_MARKER_KEY = "migration.mongoBackupImport";

    private final ObjectMapper objectMapper;
    private final JsonUtil jsonUtil;
    private final UserRepository userRepository;
    private final DoctorRepository doctorRepository;
    private final AppointmentRepository appointmentRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final PharmacyRepository pharmacyRepository;
    private final PharmacyOrderRepository pharmacyOrderRepository;
    private final NotificationRepository notificationRepository;
    private final SettingRepository settingRepository;
    private final DoctorRequestRepository doctorRequestRepository;
    private final LabReportRepository labReportRepository;

    @Value("${app.migration.auto-import:true}")
    private boolean autoImport;

    @Value("${app.migration.backup-root:migration-backup}")
    private String backupRoot;

    public MongoBackupImportService(
        ObjectMapper objectMapper,
        JsonUtil jsonUtil,
        UserRepository userRepository,
        DoctorRepository doctorRepository,
        AppointmentRepository appointmentRepository,
        PrescriptionRepository prescriptionRepository,
        PharmacyRepository pharmacyRepository,
        PharmacyOrderRepository pharmacyOrderRepository,
        NotificationRepository notificationRepository,
        SettingRepository settingRepository,
        DoctorRequestRepository doctorRequestRepository,
        LabReportRepository labReportRepository
    ) {
        this.objectMapper = objectMapper;
        this.jsonUtil = jsonUtil;
        this.userRepository = userRepository;
        this.doctorRepository = doctorRepository;
        this.appointmentRepository = appointmentRepository;
        this.prescriptionRepository = prescriptionRepository;
        this.pharmacyRepository = pharmacyRepository;
        this.pharmacyOrderRepository = pharmacyOrderRepository;
        this.notificationRepository = notificationRepository;
        this.settingRepository = settingRepository;
        this.doctorRequestRepository = doctorRequestRepository;
        this.labReportRepository = labReportRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!autoImport) {
            return;
        }

        try {
            if (settingRepository.existsById(IMPORT_MARKER_KEY)) {
                return;
            }

            if (hasExistingDomainData()) {
                log.info("Skipping Mongo backup import because domain tables already contain data.");
                return;
            }

            Path root = resolveBackupRoot();
            if (!Files.exists(root)) {
                log.info("Mongo backup root not found at {}. Skipping import.", root);
                return;
            }

            Path backupDir = findLatestBackupDirectory(root);
            if (backupDir == null) {
                log.info("No Mongo backup directories were found in {}.", root);
                return;
            }

            Map<String, List<Map<String, Object>>> collections = loadCollections(backupDir);
            if (collections.isEmpty()) {
                log.info("No collection files found in {}. Skipping import.", backupDir);
                return;
            }

            Map<String, Integer> stats = new HashMap<>();
            stats.put("users", importUsers(collections.get("users")));
            stats.put("doctors", importDoctors(collections.get("doctors")));
            stats.put("appointments", importAppointments(collections.get("appointments")));
            stats.put("prescriptions", importPrescriptions(collections.get("prescriptions")));

            List<Map<String, Object>> pharmacies = collections.get("pharmacies");
            if ((pharmacies == null || pharmacies.isEmpty()) && collections.containsKey("pharmacists")) {
                pharmacies = collections.get("pharmacists");
            }
            stats.put("pharmacies", importPharmacies(pharmacies));

            stats.put("pharmacyorders", importPharmacyOrders(collections.get("pharmacyorders")));
            stats.put("notifications", importNotifications(collections.get("notifications")));
            stats.put("settings", importSettings(collections.get("settings")));
            stats.put("doctorrequests", importDoctorRequests(collections.get("doctorrequests")));
            stats.put("labreports", importLabReports(collections.get("labreports")));

            Instant now = Instant.now();
            SettingEntity marker = settingRepository.findById(IMPORT_MARKER_KEY).orElseGet(() -> SettingEntity.builder().key(IMPORT_MARKER_KEY).createdAt(now).build());
            Map<String, Object> markerValue = new HashMap<>();
            markerValue.put("status", "completed");
            markerValue.put("backupDir", backupDir.getFileName().toString());
            markerValue.put("importedAt", now.toString());
            markerValue.put("stats", stats);
            marker.setValueJson(jsonUtil.toJson(markerValue));
            marker.setUpdatedAt(now);
            settingRepository.save(marker);

            log.info("Mongo backup import completed from {} with stats: {}", backupDir, stats);
        } catch (Exception ex) {
            log.error("Mongo backup import failed. Startup will continue.", ex);
        }
    }

    private boolean hasExistingDomainData() {
        return userRepository.count() > 0
            || doctorRepository.count() > 0
            || appointmentRepository.count() > 0
            || pharmacyRepository.count() > 0
            || pharmacyOrderRepository.count() > 0
            || prescriptionRepository.count() > 0;
    }

    private Path resolveBackupRoot() {
        Path configured = Paths.get(backupRoot);
        if (configured.isAbsolute()) {
            return configured;
        }

        Path cwd = Paths.get("").toAbsolutePath().normalize();
        Path direct = cwd.resolve(configured).normalize();
        if (Files.exists(direct)) {
            return direct;
        }

        Path nested = cwd.resolve("backend").resolve(configured).normalize();
        if (Files.exists(nested)) {
            return nested;
        }

        return direct;
    }

    private Path findLatestBackupDirectory(Path root) throws IOException {
        try (Stream<Path> stream = Files.list(root)) {
            return stream
                .filter(Files::isDirectory)
                .max(Comparator.comparingLong(path -> path.toFile().lastModified()))
                .orElse(null);
        }
    }

    private Map<String, List<Map<String, Object>>> loadCollections(Path backupDir) throws IOException {
        Path manifestPath = backupDir.resolve("manifest.json");
        Map<String, List<Map<String, Object>>> collections = new HashMap<>();

        if (Files.exists(manifestPath)) {
            JsonNode manifest = objectMapper.readTree(Files.readString(manifestPath));
            JsonNode list = manifest.path("collections");
            if (list.isArray()) {
                for (JsonNode item : list) {
                    String collectionName = item.path("collection").asText("").trim().toLowerCase(Locale.ROOT);
                    String file = item.path("file").asText("").trim();
                    if (collectionName.isEmpty() || file.isEmpty()) {
                        continue;
                    }
                    Path collectionPath = backupDir.resolve(file);
                    if (Files.exists(collectionPath)) {
                        collections.put(collectionName, loadCollectionFile(collectionPath));
                    }
                }
            }
        }

        if (!collections.isEmpty()) {
            return collections;
        }

        try (Stream<Path> stream = Files.list(backupDir)) {
            stream
                .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".ejson"))
                .forEach(path -> {
                    String filename = path.getFileName().toString();
                    String name = filename.substring(0, filename.length() - ".ejson".length()).toLowerCase(Locale.ROOT);
                    try {
                        collections.put(name, loadCollectionFile(path));
                    } catch (IOException ex) {
                        log.warn("Failed to read collection file {}", path, ex);
                    }
                });
        }

        return collections;
    }

    private List<Map<String, Object>> loadCollectionFile(Path filePath) throws IOException {
        String content = Files.readString(filePath);
        JsonNode root = objectMapper.readTree(content);

        List<Map<String, Object>> docs = new ArrayList<>();
        if (!root.isArray()) {
            return docs;
        }

        for (JsonNode node : root) {
            Object normalized = normalizeNode(node);
            if (normalized instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typed = (Map<String, Object>) map;
                docs.add(typed);
            }
        }

        return docs;
    }

    private Object normalizeNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }

        if (node.isObject()) {
            if (node.size() == 1 && node.has("$oid")) {
                return node.get("$oid").asText();
            }

            if (node.size() == 1 && node.has("$date")) {
                JsonNode dateNode = node.get("$date");
                if (dateNode.isTextual()) {
                    return dateNode.asText();
                }
                if (dateNode.isNumber()) {
                    return Instant.ofEpochMilli(dateNode.asLong()).toString();
                }
                if (dateNode.isObject() && dateNode.has("$numberLong")) {
                    try {
                        long epoch = Long.parseLong(dateNode.get("$numberLong").asText());
                        return Instant.ofEpochMilli(epoch).toString();
                    } catch (NumberFormatException ignored) {
                        return dateNode.get("$numberLong").asText();
                    }
                }
                return normalizeNode(dateNode);
            }

            Map<String, Object> map = new HashMap<>();
            node.fields().forEachRemaining(entry -> map.put(entry.getKey(), normalizeNode(entry.getValue())));
            return map;
        }

        if (node.isArray()) {
            List<Object> list = new ArrayList<>();
            node.forEach(item -> list.add(normalizeNode(item)));
            return list;
        }

        if (node.isBoolean()) {
            return node.booleanValue();
        }

        if (node.isIntegralNumber()) {
            return node.longValue();
        }

        if (node.isFloatingPointNumber()) {
            return node.doubleValue();
        }

        if (node.isTextual()) {
            return node.asText();
        }

        return node.toString();
    }

    private int importUsers(List<Map<String, Object>> docs) {
        if (docs == null || docs.isEmpty()) return 0;

        List<UserEntity> entities = new ArrayList<>();
        for (Map<String, Object> doc : docs) {
            String id = str(doc.get("_id"));
            if (isBlank(id)) continue;

            UserEntity entity = UserEntity.builder()
                .id(id)
                .name(defaultStr(doc.get("name"), "Unknown User"))
                .email(defaultEmail(doc.get("email"), id))
                .image(str(doc.get("image")))
                .phone(str(doc.get("phone")))
                .addressJson(jsonUtil.toJson(orDefault(doc.get("address"), Map.of())))
                .deliveryAddressesJson(jsonUtil.toJson(orDefault(doc.get("deliveryAddresses"), List.of())))
                .defaultPharmacyId(str(doc.get("defaultPharmacyId")))
                .gender(str(doc.get("gender")))
                .dob(str(doc.get("dob")))
                .password(defaultStr(doc.get("password"), ""))
                .build();

            entities.add(entity);
        }

        userRepository.saveAll(entities);
        return entities.size();
    }

    private int importDoctors(List<Map<String, Object>> docs) {
        if (docs == null || docs.isEmpty()) return 0;

        List<DoctorEntity> entities = new ArrayList<>();
        for (Map<String, Object> doc : docs) {
            String id = str(doc.get("_id"));
            if (isBlank(id)) continue;

            DoctorEntity entity = DoctorEntity.builder()
                .id(id)
                .name(defaultStr(doc.get("name"), "Doctor"))
                .email(defaultEmail(doc.get("email"), id))
                .password(defaultStr(doc.get("password"), ""))
                .image(str(doc.get("image")))
                .speciality(defaultStr(doc.get("speciality"), "General physician"))
                .degree(defaultStr(doc.get("degree"), "MBBS"))
                .experience(defaultStr(doc.get("experience"), "0 Year"))
                .about(defaultStr(doc.get("about"), ""))
                .available(bool(doc.get("available"), true))
                .fees(dbl(doc.get("fees"), 0D))
                .slotsBookedJson(jsonUtil.toJson(orDefault(doc.get("slots_booked"), Map.of())))
                .addressJson(jsonUtil.toJson(orDefault(doc.get("address"), Map.of())))
                .date(lng(doc.get("date"), System.currentTimeMillis()))
                .build();

            entities.add(entity);
        }

        doctorRepository.saveAll(entities);
        return entities.size();
    }

    private int importAppointments(List<Map<String, Object>> docs) {
        if (docs == null || docs.isEmpty()) return 0;

        List<AppointmentEntity> entities = new ArrayList<>();
        for (Map<String, Object> doc : docs) {
            String id = str(doc.get("_id"));
            if (isBlank(id)) continue;

            AppointmentEntity entity = AppointmentEntity.builder()
                .id(id)
                .userId(str(doc.get("userId")))
                .docId(str(doc.get("docId")))
                .slotDate(defaultStr(doc.get("slotDate"), ""))
                .slotTime(defaultStr(doc.get("slotTime"), ""))
                .userDataJson(jsonUtil.toJson(orDefault(doc.get("userData"), Map.of())))
                .docDataJson(jsonUtil.toJson(orDefault(doc.get("docData"), Map.of())))
                .amount(dbl(doc.get("amount"), 0D))
                .date(lng(doc.get("date"), System.currentTimeMillis()))
                .cancelled(bool(doc.get("cancelled"), false))
                .payment(bool(doc.get("payment"), false))
                .isCompleted(bool(doc.get("isCompleted"), false))
                .prescriptionId(str(doc.get("prescriptionId")))
                .pharmacyOrderId(str(doc.get("pharmacyOrderId")))
                .consultationJson(jsonUtil.toJson(orDefault(doc.get("consultation"), Map.of())))
                .build();

            entities.add(entity);
        }

        appointmentRepository.saveAll(entities);
        return entities.size();
    }

    private int importPrescriptions(List<Map<String, Object>> docs) {
        if (docs == null || docs.isEmpty()) return 0;

        List<PrescriptionEntity> entities = new ArrayList<>();
        for (Map<String, Object> doc : docs) {
            String id = str(doc.get("_id"));
            if (isBlank(id)) continue;

            PrescriptionEntity entity = PrescriptionEntity.builder()
                .id(id)
                .appointmentId(defaultStr(doc.get("appointmentId"), ""))
                .userId(defaultStr(doc.get("userId"), ""))
                .docId(defaultStr(doc.get("docId"), ""))
                .diagnosis(str(doc.get("diagnosis")))
                .clinicalNotes(str(doc.get("clinicalNotes")))
                .medicationsJson(jsonUtil.toJson(orDefault(doc.get("medications"), List.of())))
                .investigationsJson(jsonUtil.toJson(orDefault(doc.get("investigations"), List.of())))
                .followUpDate(instant(doc.get("followUpDate")))
                .lifestyleAdvice(str(doc.get("lifestyleAdvice")))
                .attachmentsJson(jsonUtil.toJson(orDefault(doc.get("attachments"), List.of())))
                .preferredPharmaciesJson(jsonUtil.toJson(orDefault(doc.get("preferredPharmacies"), List.of())))
                .issuedAt(instant(doc.get("issuedAt")))
                .lastUpdatedBy(str(doc.get("lastUpdatedBy")))
                .createdAt(instant(doc.get("createdAt")))
                .updatedAt(instant(doc.get("updatedAt")))
                .build();

            entities.add(entity);
        }

        prescriptionRepository.saveAll(entities);
        return entities.size();
    }

    private int importPharmacies(List<Map<String, Object>> docs) {
        if (docs == null || docs.isEmpty()) return 0;

        List<PharmacyEntity> entities = new ArrayList<>();
        for (Map<String, Object> doc : docs) {
            String id = str(doc.get("_id"));
            if (isBlank(id)) continue;

            PharmacyEntity entity = PharmacyEntity.builder()
                .id(id)
                .name(defaultStr(doc.get("name"), "Pharmacy"))
                .email(defaultEmail(doc.get("email"), id))
                .password(defaultStr(doc.get("password"), ""))
                .ownerName(defaultStr(doc.get("ownerName"), "Owner"))
                .phone(defaultStr(doc.get("phone"), ""))
                .alternatePhone(str(doc.get("alternatePhone")))
                .addressJson(jsonUtil.toJson(orDefault(doc.get("address"), Map.of())))
                .licenseNumber(defaultStr(doc.get("licenseNumber"), id))
                .licenseDocumentsJson(jsonUtil.toJson(orDefault(doc.get("licenseDocuments"), List.of())))
                .gstNumber(str(doc.get("gstNumber")))
                .deliveryOptionsJson(jsonUtil.toJson(orDefault(doc.get("deliveryOptions"), List.of())))
                .operatingHoursJson(jsonUtil.toJson(orDefault(doc.get("operatingHours"), Map.of())))
                .serviceRadiusKm(dbl(doc.get("serviceRadiusKm"), null))
                .isApproved(bool(doc.get("isApproved"), false))
                .approvedAt(instant(doc.get("approvedAt")))
                .approvedBy(str(doc.get("approvedBy")))
                .isActive(bool(doc.get("isActive"), false))
                .moderationNotes(str(doc.get("moderationNotes")))
                .payoutDetailsJson(jsonUtil.toJson(orDefault(doc.get("payoutDetails"), Map.of())))
                .lastLoginAt(instant(doc.get("lastLoginAt")))
                .createdAt(instant(doc.get("createdAt")))
                .updatedAt(instant(doc.get("updatedAt")))
                .build();

            entities.add(entity);
        }

        pharmacyRepository.saveAll(entities);
        return entities.size();
    }

    private int importPharmacyOrders(List<Map<String, Object>> docs) {
        if (docs == null || docs.isEmpty()) return 0;

        List<PharmacyOrderEntity> entities = new ArrayList<>();
        for (Map<String, Object> doc : docs) {
            String id = str(doc.get("_id"));
            if (isBlank(id)) continue;

            PharmacyOrderEntity entity = PharmacyOrderEntity.builder()
                .id(id)
                .orderNumber(defaultStr(doc.get("orderNumber"), "RX" + id))
                .prescriptionId(defaultStr(doc.get("prescriptionId"), ""))
                .appointmentId(defaultStr(doc.get("appointmentId"), ""))
                .userId(defaultStr(doc.get("userId"), ""))
                .pharmacyId(defaultStr(doc.get("pharmacyId"), ""))
                .status(defaultStr(doc.get("status"), "pending"))
                .statusHistoryJson(jsonUtil.toJson(orDefault(doc.get("statusHistory"), List.of())))
                .logisticsJson(jsonUtil.toJson(orDefault(doc.get("logistics"), Map.of())))
                .notesForPatient(str(doc.get("notesForPatient")))
                .notesForInternal(str(doc.get("notesForInternal")))
                .prescriptionSnapshotJson(jsonUtil.toJson(orDefault(doc.get("prescriptionSnapshot"), Map.of())))
                .patientSnapshotJson(jsonUtil.toJson(orDefault(doc.get("patientSnapshot"), Map.of())))
                .totalAmount(dbl(doc.get("totalAmount"), null))
                .paymentStatus(str(doc.get("paymentStatus")))
                .createdVia(str(doc.get("createdVia")))
                .createdAt(instant(doc.get("createdAt")))
                .updatedAt(instant(doc.get("updatedAt")))
                .build();

            entities.add(entity);
        }

        pharmacyOrderRepository.saveAll(entities);
        return entities.size();
    }

    private int importNotifications(List<Map<String, Object>> docs) {
        if (docs == null || docs.isEmpty()) return 0;

        List<NotificationEntity> entities = new ArrayList<>();
        for (Map<String, Object> doc : docs) {
            String id = str(doc.get("_id"));
            if (isBlank(id)) continue;

            NotificationEntity entity = NotificationEntity.builder()
                .id(id)
                .recipientId(defaultStr(doc.get("recipientId"), ""))
                .recipientRole(defaultStr(doc.get("recipientRole"), "patient"))
                .title(defaultStr(doc.get("title"), "Notification"))
                .message(defaultStr(doc.get("message"), ""))
                .metadataJson(jsonUtil.toJson(orDefault(doc.get("metadata"), Map.of())))
                .isRead(bool(doc.get("isRead"), false))
                .readAt(instant(doc.get("readAt")))
                .createdAt(instant(doc.get("createdAt")))
                .updatedAt(instant(doc.get("updatedAt")))
                .build();

            entities.add(entity);
        }

        notificationRepository.saveAll(entities);
        return entities.size();
    }

    private int importSettings(List<Map<String, Object>> docs) {
        if (docs == null || docs.isEmpty()) return 0;

        List<SettingEntity> entities = new ArrayList<>();
        for (Map<String, Object> doc : docs) {
            String key = str(doc.get("key"));
            if (isBlank(key)) {
                continue;
            }

            SettingEntity entity = SettingEntity.builder()
                .key(key)
                .valueJson(jsonUtil.toJson(orDefault(doc.get("value"), Map.of())))
                .createdAt(instant(doc.get("createdAt")))
                .updatedAt(instant(doc.get("updatedAt")))
                .build();

            entities.add(entity);
        }

        settingRepository.saveAll(entities);
        return entities.size();
    }

    private int importDoctorRequests(List<Map<String, Object>> docs) {
        if (docs == null || docs.isEmpty()) return 0;

        List<DoctorRequestEntity> entities = new ArrayList<>();
        for (Map<String, Object> doc : docs) {
            String id = str(doc.get("_id"));
            if (isBlank(id)) continue;

            DoctorRequestEntity entity = DoctorRequestEntity.builder()
                .id(id)
                .name(defaultStr(doc.get("name"), ""))
                .email(defaultEmail(doc.get("email"), id))
                .phone(str(doc.get("phone")))
                .speciality(str(doc.get("speciality")))
                .message(str(doc.get("message")))
                .status(defaultStr(doc.get("status"), "pending"))
                .createdAt(instant(doc.get("createdAt")))
                .build();

            entities.add(entity);
        }

        doctorRequestRepository.saveAll(entities);
        return entities.size();
    }

    private int importLabReports(List<Map<String, Object>> docs) {
        if (docs == null || docs.isEmpty()) return 0;

        List<LabReportEntity> entities = new ArrayList<>();
        for (Map<String, Object> doc : docs) {
            String id = str(doc.get("_id"));
            if (isBlank(id)) continue;

            LabReportEntity entity = LabReportEntity.builder()
                .id(id)
                .appointmentId(defaultStr(doc.get("appointmentId"), ""))
                .userId(defaultStr(doc.get("userId"), ""))
                .docId(defaultStr(doc.get("docId"), ""))
                .title(defaultStr(doc.get("title"), "Report"))
                .description(str(doc.get("description")))
                .fileUrl(str(doc.get("fileUrl")))
                .uploadedBy(str(doc.get("uploadedBy")))
                .metadataJson(jsonUtil.toJson(orDefault(doc.get("metadata"), Map.of())))
                .createdAt(instant(doc.get("createdAt")))
                .updatedAt(instant(doc.get("updatedAt")))
                .build();

            entities.add(entity);
        }

        labReportRepository.saveAll(entities);
        return entities.size();
    }

    private Object orDefault(Object value, Object fallback) {
        return value == null ? fallback : value;
    }

    private String defaultEmail(Object value, String id) {
        String email = str(value);
        if (!isBlank(email) && email.contains("@")) {
            return email.toLowerCase(Locale.ROOT);
        }
        return id + "@migrated.local";
    }

    private String defaultStr(Object value, String fallback) {
        String text = str(value);
        return isBlank(text) ? fallback : text;
    }

    private String str(Object value) {
        if (value == null) return null;
        return String.valueOf(value);
    }

    private Boolean bool(Object value, Boolean fallback) {
        if (value == null) return fallback;
        if (value instanceof Boolean bool) return bool;
        String text = String.valueOf(value);
        if ("true".equalsIgnoreCase(text) || "1".equals(text)) return true;
        if ("false".equalsIgnoreCase(text) || "0".equals(text)) return false;
        return fallback;
    }

    private Double dbl(Object value, Double fallback) {
        if (value == null) return fallback;
        if (value instanceof Number number) return number.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private Long lng(Object value, Long fallback) {
        if (value == null) return fallback;
        if (value instanceof Number number) return number.longValue();
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private Instant instant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof Number number) {
            return Instant.ofEpochMilli(number.longValue());
        }

        String text = String.valueOf(value);
        if (isBlank(text)) {
            return null;
        }

        try {
            return Instant.parse(text);
        } catch (DateTimeParseException ignored) {
        }

        try {
            return Instant.ofEpochMilli(Long.parseLong(text));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
