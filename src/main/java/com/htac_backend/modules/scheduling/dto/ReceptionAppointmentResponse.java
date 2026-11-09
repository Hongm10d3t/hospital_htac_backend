package com.htac_backend.modules.scheduling.dto;

public record ReceptionAppointmentResponse(AppointmentResponse appointment, Long patientId,
        String patientName, String phone, String reason) { }
