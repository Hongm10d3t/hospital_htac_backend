# M05.04 — Đơn bổ sung, sửa nháp và hủy đơn

Bác sĩ được phân công có thể tạo nhiều đơn trên cùng lượt IN_PROGRESS hoặc COMPLETED qua API M05.03. Đơn bổ sung là bản ghi và mã đơn riêng, giữ nguyên `visitId` và bác sĩ kê.

- `PUT /api/v1/doctor/prescriptions/{prescriptionId}`: thay `note` và toàn bộ `items` của đơn DRAFT bằng body như lúc tạo đơn. Thuốc phải đang hoạt động, lượng và hướng dẫn hợp lệ. Không cho sửa đơn ISSUED/CANCELLED hoặc đơn đã có phiếu kho đang hiệu lực.
- `POST /api/v1/doctor/prescriptions/{prescriptionId}/cancel`: body `{"reason":"<lý do, tối đa 3000 ký tự>"}`. DRAFT hoặc ISSUED có thể hủy nếu chưa có phiếu kho DRAFT/POSTED. Lý do được nối vào `note` để giữ ghi chú trước đó. Đơn đã ISSUED giữ `issuedAt` và các dòng cũ để bệnh nhân xem trạng thái hủy. Gửi lặp cùng lý do trả kết quả cũ; lý do khác bị từ chối.

Đơn ISSUED bất biến về thuốc/lượng/hướng dẫn. Lượt IN_PROGRESS không được chuyển NOT_COMPLETED khi còn đơn ISSUED; bác sĩ phải xử lý đơn trước. M05 khóa dòng `prescription` khi phát hành, sửa hoặc hủy. M06 phải gọi `IssuedPrescriptionService.lockForDispensing` trước khi lập/ghi sổ cấp thuốc và giữ khóa đến lúc commit. Khi hủy, M05 kiểm tra `stock_document` qua service M06 trong cùng giao dịch; nếu đã có phiếu đang hiệu lực trả 409 `PRESCRIPTION_HAS_STOCK_DOCUMENT`. Sửa đơn đã phát hành trả 409 `PRESCRIPTION_IMMUTABLE`; lý do sai trả 400.

Kiểm thử hiện tại xác nhận cạnh tranh ở hợp đồng khóa với phiếu POSTED mô phỏng. Đối chiếu lượng kê và lượng thực cấp cần được nghiệm thu cùng luồng DISPENSE M06. Không có migration mới.
