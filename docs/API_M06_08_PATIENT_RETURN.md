# M06.08 — Nhận thuốc bệnh nhân trả

`POST /api/v1/pharmacy/stock-documents/patient-returns` yêu cầu PHARMACIST đang hoạt động và CSRF, tạo PATIENT_RETURN DRAFT, trả 201. Body `{"prescriptionId":1,"occurredAt":"2026-11-10T10:00:00+07:00","reason":"<lý do trả>","items":[{"sourceItemId":5,"prescriptionItemId":2,"batchId":3,"quantity":1.000,"condition":"SEALED","note":"<tùy chọn>"}]}`.

Mỗi dòng phải truy về dòng DISPENSE POSTED của chính đơn, dòng đơn và lô nêu trong yêu cầu. `condition` nhận SEALED, OPENED, DAMAGED hoặc UNKNOWN; lý do trả bắt buộc. Tổng PATIENT_RETURN POSTED của một `sourceItemId` qua mọi đợt không vượt lượng cấp gốc. Tạo nháp và ghi sổ đều kiểm tra, ghi sổ khóa đơn để hai lượt trả cạnh tranh không vượt lượng gốc.

`POST /api/v1/pharmacy/stock-documents/{id}/post` cộng lượng trả vào COUNTER/QUARANTINE. Tồn COUNTER/AVAILABLE không tăng; chỉ có thể chuyển từ cách ly về khả dụng theo M06.05 sau khi xác nhận xử lý. Lượng trả không trừ tổng DISPENSE đã cấp và không mở thêm quyền cấp của đơn cũ; nếu cần cấp thay thế, bác sĩ lập đơn bổ sung. Không có migration mới.
