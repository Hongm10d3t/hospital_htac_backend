package com.htac_backend.modules.outpatient.dto;

public record VisitOutcome(Long patientId, Long slotId, String status, String outcomeReason) { }
