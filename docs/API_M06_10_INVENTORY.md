# M06.10 — Tra cứu tồn và cảnh báo

Các API `/api/v1/pharmacy/inventory` yêu cầu PHARMACIST đang hoạt động.

- `GET /api/v1/pharmacy/inventory?term=&active=&page=&size=&sort=`: danh sách thuốc có phân trang, tổng tồn vật lý, tổng tồn xét cấp, ngưỡng `minimumStock`, cờ `belowMinimum`, cảnh báo hạn dùng và các số dư chi tiết theo lô/vị trí/nhóm. Sort nhận `id`, `code`, `name`.
- `GET /api/v1/pharmacy/inventory/medicines/{id}`: cùng dữ liệu cho một thuốc, kể cả thuốc đã ngừng hoạt động.
- `GET /api/v1/pharmacy/inventory/batches/{id}/movements?location=&bucket=&page=&size=&sort=`: các biến động POSTED của lô có dấu cộng/trừ, mã phiếu, dòng phiếu, loại phiếu và thời điểm nghiệp vụ; có thể lọc MAIN/COUNTER và AVAILABLE/QUARANTINE. Chuyển kho xuất hiện một dòng trừ ở nguồn và một dòng cộng ở đích. Sort nhận `occurredAt`, `documentId`, `itemId`.

Số dư tính trực tiếp từ phiếu POSTED: OPENING/RECEIPT/PATIENT_RETURN cộng, DISPENSE/SUPPLIER_RETURN/DISPOSAL trừ, ADJUSTMENT dùng lượng có dấu, TRANSFER trừ nguồn và cộng đích. `physicalQuantity` cộng tất cả vị trí và nhóm tồn. `dispensableQuantity` chỉ cộng AVAILABLE của lô ACTIVE, thuốc đang hoạt động và `expiryDate` sau ngày tại `Asia/Ho_Chi_Minh`. `belowMinimum` so sánh tổng khả dụng toàn viện với ngưỡng thuốc đang hoạt động. Cảnh báo hạn dùng dựa trên tồn vật lý dương, gồm cả lô khóa/cách ly và lô đã hết hạn; phần `expired` tách lô quá hạn.

Ngưỡng cảnh báo cấu hình bằng `HIS_PHARMACY_EXPIRY_WARNING_DAYS` (mặc định 30 ngày, cho phép 0–3650), được trả trong `expiryWarningDays`. Chi tiết cộng lên đúng tổng; mỗi biến động truy về phiếu qua `documentId`. Không có migration mới.
