package com.htac_backend.modules.outpatient.dto;

import java.util.Set;

public record VisitOccupancy(int actualVisits, Set<Long> receivedAppointmentIds) { }
