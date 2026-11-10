package com.htac_backend.modules.scheduling.dto;

public record ReceptionBooking(Long appointmentId, Long patientId, Long slotId, String reason) { }
