package com.htac_backend.modules.outpatient.service;

import com.htac_backend.modules.outpatient.dto.PrescriptionLineRequest;
import com.htac_backend.shared.api.InvalidRequestException;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;

final class PrescriptionLineValidator {
    private PrescriptionLineValidator() { }

    static void validate(List<PrescriptionLineRequest> lines) {
        if (lines == null || lines.isEmpty() || lines.size() > 100) {
            throw new InvalidRequestException("Đơn thuốc cần từ 1 đến 100 dòng thuốc");
        }
        var seen = new HashSet<Long>();
        for (PrescriptionLineRequest line : lines) {
            if (line == null || line.medicineId() == null || !seen.add(line.medicineId())
                    || line.quantity() == null || line.quantity().compareTo(BigDecimal.ZERO) <= 0
                    || line.quantity().precision() - line.quantity().scale() > 11
                    || line.quantity().scale() > 3 || line.usageInstruction() == null
                    || line.usageInstruction().isBlank() || line.usageInstruction().length() > 3000) {
                throw new InvalidRequestException("Dòng thuốc không hợp lệ hoặc trùng thuốc");
            }
        }
    }
}
