# M03.04 — Xác nhận, theo dõi và kết thúc lịch hẹn

## Lịch cá nhân của PATIENT

- `GET /api/v1/appointments`: danh sách của chính hồ sơ bệnh nhân liên kết tài khoản hiện tại. Phân trang `page`, `size`, `sort=id|createdAt,asc|desc`.
- `GET /api/v1/appointments/{id}`: chi tiết của chính mình; ID của người khác trả 404. DTO gồm mã hẹn, slot, bác sĩ, phòng, thời gian, trạng thái, thời điểm xác nhận/kết thúc và lý do không hoàn thành nếu có; không trả số liên hệ hoặc dữ liệu bệnh nhân khác.

## Xử lý của RECEPTIONIST

Các API dưới `/api/v1/reception/appointments` chỉ dành cho RECEPTIONIST và dùng session/CSRF cho thao tác ghi.

- `GET` với `status`, `date=YYYY-MM-DD`, phân trang: lọc theo trạng thái và ngày slot tại `Asia/Ho_Chi_Minh`. Trả thông tin hẹn cùng tên/số điện thoại của bệnh nhân để gọi xác nhận. Số liên hệ đọc từ `user_account.phone` qua service M01; không sao chép vào `patient` hoặc `appointment`.
- `GET /{id}`: chi tiết phục vụ xác nhận.
- `POST /{id}/confirm`: PENDING → CONFIRMED, lưu `confirmed_by` và `confirmed_at`. Gọi lặp trên CONFIRMED trả cùng trạng thái; hẹn đã kết thúc trả 409 `APPOINTMENT_FINAL`.
- `POST /{id}/not-complete` với `{"reason":"<lý do>"}`: PENDING/CONFIRMED → NOT_COMPLETED cho hẹn chưa có lượt thực tế, lưu `closed_at` và `non_completion_reason`. Lý do không trắng, tối đa 3000 ký tự. Lượt đã tồn tại phải được kết thúc từ quy trình khám; trả 409 `APPOINTMENT_HAS_VISIT`. Gửi lặp cùng lý do trả cùng kết quả; khác lý do trả `APPOINTMENT_FINAL`.
- `POST /{id}/complete`: chỉ chuyển PENDING/CONFIRMED → COMPLETED nếu lượt liên kết đã ở trạng thái COMPLETED và đúng patient/slot. Thiếu lượt hoặc lượt chưa hoàn thành trả `VISIT_REQUIRED`/`VISIT_NOT_COMPLETED`. Gọi lặp trên COMPLETED giữ nguyên kết quả.

M03 cung cấp `ReceptionAppointmentService.completeFromVisit` và `notCompleteFromVisit` để M05 gọi trong **cùng giao dịch READ_COMMITTED** sau khi cập nhật lượt tương ứng. Hai phương thức kiểm tra trạng thái lượt từ service đọc M05, khóa slot rồi khóa appointment; không cho thay kết quả cuối khác kết quả đã ghi. Lượt `NOT_COMPLETED` vẫn là lượt đã đến; hẹn `NOT_COMPLETED` chưa tiếp nhận không tạo lượt. `VOIDED` không được xem là lượt thực tế để hoàn tất hẹn. M05 cần gọi hợp đồng này khi triển khai luồng khám thật; kiểm thử hiện tại dùng bản ghi lượt MySQL để xác nhận quy tắc.

Không có migration mới. Thao tác chốt trạng thái dùng giao dịch READ_COMMITTED; chỉ đọc ID slot dạng scalar trước khi khóa, rồi mới nạp entity hẹn để tránh trạng thái cũ trong persistence context khi hai yêu cầu cùng cập nhật.
