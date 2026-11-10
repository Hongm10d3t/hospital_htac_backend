# M04.03 — Tiếp nhận và danh sách chờ

Các API dưới `/api/v1/reception/visits` chỉ dành cho RECEPTIONIST đang hoạt động. POST cần CSRF token. Ngày nghiệp vụ dùng `Asia/Ho_Chi_Minh`; chỉ tiếp nhận vào ngày của khung khám. Khung phải có phòng và bác sĩ đã phân công; danh sách chờ trả `slotId` để xác định bác sĩ phụ trách theo khung. Thời điểm tiếp nhận là thời gian backend, không nhận từ client.

- `POST /appointments/{appointmentId}`: tiếp nhận lịch PENDING/CONFIRMED còn giữ suất. Dùng patient và slot của lịch, sao chép lý do. Trả 200 và bản ghi lượt; gọi lại cùng hẹn trả đúng lượt cũ, kể cả khi trạng thái lượt đã thay đổi. Lịch giữ suất được chuyển sang lượt thực tế trong cách tính sức chứa, không cộng hai lần.
- `POST /direct`: body `{"patientId":1,"slotId":2,"reason":"<tùy chọn>"}`. Chỉ khung OPEN còn chỗ; lưu `appointmentId=null`. Trả 201.
- `GET /waiting?date=YYYY-MM-DD&roomId=...&slotId=...&page=0&size=20&sort=queueNumber,ASC`: danh sách WAITING, mặc định ngày hiện tại, có thể lọc phòng hoặc khung. `sort` hỗ trợ `queueNumber` và `id`. Trả `PageResponse` theo quy ước chung.

Lượt trả `id`, `visitCode`, `patientId`, `appointmentId`, `slotId`, `roomId`, `queueDate`, `queueNumber`, `arrivedAt`, `status`, `reason`, `outcomeReason`; không trả nội dung chuyên môn. Slot và phòng bị khóa trong giao dịch `READ_COMMITTED` trước khi ghi lượt. Số thứ tự là phạm vi phòng/ngày; M04.04 bổ sung quy tắc chuyển trạng thái và kiểm thử phạm vi số.

Lỗi nghiệp vụ 409 gồm `SLOT_NOT_TODAY`, `SLOT_CLOSED`, `SLOT_FULL`, `APPOINTMENT_NOT_HELD`, `ROOM_INACTIVE`. Patient, slot hoặc appointment không tồn tại trả 404; dữ liệu sai trả 400. Không có migration mới.
