package com.prescripto.backend.service;

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
import com.prescripto.backend.util.JsonUtil;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class MapperService {

    private final JsonUtil jsonUtil;

    public MapperService(JsonUtil jsonUtil) {
        this.jsonUtil = jsonUtil;
    }

    public Map<String, Object> user(UserEntity entity, boolean includePassword) {
        Map<String, Object> map = new HashMap<>();
        map.put("_id", entity.getId());
        map.put("name", entity.getName());
        map.put("email", entity.getEmail());
        map.put("image", entity.getImage());
        map.put("phone", entity.getPhone());
        map.put("address", jsonUtil.toObject(entity.getAddressJson()));
        map.put("deliveryAddresses", jsonUtil.toObject(entity.getDeliveryAddressesJson()));
        map.put("defaultPharmacyId", entity.getDefaultPharmacyId());
        map.put("gender", entity.getGender());
        map.put("dob", entity.getDob());
        if (includePassword) {
            map.put("password", entity.getPassword());
        }
        return map;
    }

    public Map<String, Object> doctor(DoctorEntity entity, boolean includeSensitive) {
        Map<String, Object> map = new HashMap<>();
        map.put("_id", entity.getId());
        map.put("name", entity.getName());
        map.put("email", entity.getEmail());
        map.put("image", entity.getImage());
        map.put("speciality", entity.getSpeciality());
        map.put("degree", entity.getDegree());
        map.put("experience", entity.getExperience());
        map.put("about", entity.getAbout());
        map.put("available", entity.getAvailable());
        map.put("fees", entity.getFees());
        map.put("slots_booked", jsonUtil.toObject(entity.getSlotsBookedJson()));
        map.put("address", jsonUtil.toObject(entity.getAddressJson()));
        map.put("date", entity.getDate());
        if (includeSensitive) {
            map.put("password", entity.getPassword());
        }
        return map;
    }

    public Map<String, Object> appointment(AppointmentEntity entity) {
        Map<String, Object> map = new HashMap<>();
        map.put("_id", entity.getId());
        map.put("userId", entity.getUserId());
        map.put("docId", entity.getDocId());
        map.put("slotDate", entity.getSlotDate());
        map.put("slotTime", entity.getSlotTime());
        map.put("userData", jsonUtil.toObject(entity.getUserDataJson()));
        map.put("docData", jsonUtil.toObject(entity.getDocDataJson()));
        map.put("amount", entity.getAmount());
        map.put("date", entity.getDate());
        map.put("cancelled", entity.getCancelled());
        map.put("payment", entity.getPayment());
        map.put("isCompleted", entity.getIsCompleted());
        map.put("prescriptionId", entity.getPrescriptionId());
        map.put("pharmacyOrderId", entity.getPharmacyOrderId());
        map.put("consultation", jsonUtil.toObject(entity.getConsultationJson()));
        return map;
    }

    public Map<String, Object> prescription(PrescriptionEntity entity) {
        Map<String, Object> map = new HashMap<>();
        map.put("_id", entity.getId());
        map.put("appointmentId", entity.getAppointmentId());
        map.put("userId", entity.getUserId());
        map.put("docId", entity.getDocId());
        map.put("diagnosis", entity.getDiagnosis());
        map.put("clinicalNotes", entity.getClinicalNotes());
        map.put("medications", jsonUtil.toObject(entity.getMedicationsJson()));
        map.put("investigations", jsonUtil.toObject(entity.getInvestigationsJson()));
        map.put("followUpDate", entity.getFollowUpDate());
        map.put("lifestyleAdvice", entity.getLifestyleAdvice());
        map.put("attachments", jsonUtil.toObject(entity.getAttachmentsJson()));
        map.put("preferredPharmacies", jsonUtil.toObject(entity.getPreferredPharmaciesJson()));
        map.put("issuedAt", entity.getIssuedAt());
        map.put("lastUpdatedBy", entity.getLastUpdatedBy());
        map.put("createdAt", entity.getCreatedAt());
        map.put("updatedAt", entity.getUpdatedAt());
        return map;
    }

    public Map<String, Object> pharmacy(PharmacyEntity entity, boolean includeSensitive) {
        Map<String, Object> map = new HashMap<>();
        map.put("_id", entity.getId());
        map.put("name", entity.getName());
        map.put("email", entity.getEmail());
        map.put("ownerName", entity.getOwnerName());
        map.put("phone", entity.getPhone());
        map.put("alternatePhone", entity.getAlternatePhone());
        map.put("address", jsonUtil.toObject(entity.getAddressJson()));
        map.put("licenseNumber", entity.getLicenseNumber());
        map.put("gstNumber", entity.getGstNumber());
        map.put("deliveryOptions", jsonUtil.toObject(entity.getDeliveryOptionsJson()));
        map.put("operatingHours", jsonUtil.toObject(entity.getOperatingHoursJson()));
        map.put("serviceRadiusKm", entity.getServiceRadiusKm());
        map.put("isApproved", entity.getIsApproved());
        map.put("approvedAt", entity.getApprovedAt());
        map.put("approvedBy", entity.getApprovedBy());
        map.put("isActive", entity.getIsActive());
        map.put("moderationNotes", entity.getModerationNotes());
        map.put("lastLoginAt", entity.getLastLoginAt());
        map.put("createdAt", entity.getCreatedAt());
        map.put("updatedAt", entity.getUpdatedAt());
        if (includeSensitive) {
            map.put("password", entity.getPassword());
            map.put("licenseDocuments", jsonUtil.toObject(entity.getLicenseDocumentsJson()));
            map.put("payoutDetails", jsonUtil.toObject(entity.getPayoutDetailsJson()));
        }
        return map;
    }

    public Map<String, Object> pharmacyOrder(PharmacyOrderEntity entity) {
        Map<String, Object> map = new HashMap<>();
        map.put("_id", entity.getId());
        map.put("orderNumber", entity.getOrderNumber());
        map.put("prescriptionId", entity.getPrescriptionId());
        map.put("appointmentId", entity.getAppointmentId());
        map.put("userId", entity.getUserId());
        map.put("pharmacyId", entity.getPharmacyId());
        map.put("status", entity.getStatus());
        map.put("statusHistory", jsonUtil.toObject(entity.getStatusHistoryJson()));
        map.put("logistics", jsonUtil.toObject(entity.getLogisticsJson()));
        map.put("notesForPatient", entity.getNotesForPatient());
        map.put("notesForInternal", entity.getNotesForInternal());
        map.put("prescriptionSnapshot", jsonUtil.toObject(entity.getPrescriptionSnapshotJson()));
        map.put("patientSnapshot", jsonUtil.toObject(entity.getPatientSnapshotJson()));
        map.put("totalAmount", entity.getTotalAmount());
        map.put("paymentStatus", entity.getPaymentStatus());
        map.put("createdVia", entity.getCreatedVia());
        map.put("createdAt", entity.getCreatedAt());
        map.put("updatedAt", entity.getUpdatedAt());
        return map;
    }

    public Map<String, Object> labReport(LabReportEntity entity) {
        Map<String, Object> map = new HashMap<>();
        map.put("_id", entity.getId());
        map.put("appointmentId", entity.getAppointmentId());
        map.put("userId", entity.getUserId());
        map.put("docId", entity.getDocId());
        map.put("title", entity.getTitle());
        map.put("description", entity.getDescription());
        map.put("fileUrl", entity.getFileUrl());
        map.put("uploadedBy", entity.getUploadedBy());
        map.put("metadata", jsonUtil.toObject(entity.getMetadataJson()));
        map.put("createdAt", entity.getCreatedAt());
        map.put("updatedAt", entity.getUpdatedAt());
        return map;
    }

    public Map<String, Object> doctorRequest(DoctorRequestEntity entity) {
        Map<String, Object> map = new HashMap<>();
        map.put("_id", entity.getId());
        map.put("name", entity.getName());
        map.put("email", entity.getEmail());
        map.put("phone", entity.getPhone());
        map.put("speciality", entity.getSpeciality());
        map.put("message", entity.getMessage());
        map.put("status", entity.getStatus());
        map.put("createdAt", entity.getCreatedAt());
        return map;
    }

    public Map<String, Object> notification(NotificationEntity entity) {
        Map<String, Object> map = new HashMap<>();
        map.put("_id", entity.getId());
        map.put("recipientId", entity.getRecipientId());
        map.put("recipientRole", entity.getRecipientRole());
        map.put("title", entity.getTitle());
        map.put("message", entity.getMessage());
        map.put("metadata", jsonUtil.toObject(entity.getMetadataJson()));
        map.put("isRead", entity.getIsRead());
        map.put("readAt", entity.getReadAt());
        map.put("createdAt", entity.getCreatedAt());
        map.put("updatedAt", entity.getUpdatedAt());
        return map;
    }

    public Map<String, Object> setting(SettingEntity entity) {
        Map<String, Object> map = new HashMap<>();
        map.put("key", entity.getKey());
        map.put("value", jsonUtil.toObject(entity.getValueJson()));
        map.put("createdAt", entity.getCreatedAt());
        map.put("updatedAt", entity.getUpdatedAt());
        return map;
    }
}
