# M05.01 — Công việc bác sĩ và bắt đầu khám

Các API dưới `/api/v1/doctor` chỉ dành cho tài khoản DOCTOR đang hoạt động. POST cần CSRF. Lịch và lượt được xác định từ ID bác sĩ trong phiên, không nhận `doctorId` do client chọn.

- `GET /slots?date=YYYY-MM-DD&page=0&size=20&sort=startAt,ASC`: khung được phân công trong ngày bệnh viện (`Asia/Ho_Chi_Minh`). Mặc định hôm nay; sort hỗ trợ `startAt`, `id`.
- `GET /slots/{slotId}/waiting?page=0&size=20&sort=queueNumber,ASC`: bệnh nhân WAITING trong đúng khung của bác sĩ; gồm mã bệnh nhân, họ tên, ngày sinh, giới tính và thông tin lượt. Sort hỗ trợ `queueNumber`, `id`.
- `GET /visits/{visitId}`: chi tiết lượt được phân công cho bác sĩ, gồm ghi nhận chuyên môn hiện có.
- `POST /visits/{visitId}/start`: WAITING → IN_PROGRESS, ghi `startedAt` từ backend. Gọi lại khi IN_PROGRESS trả thời điểm cũ. Lượt ở trạng thái cuối trả 409 `VISIT_NOT_STARTABLE`.
- `GET /visits/{visitId}/history`: lịch sử của bệnh nhân đang có lượt WAITING/IN_PROGRESS được phân công cho bác sĩ; hỗ trợ `page`, `size`, sort `arrivedAt` hoặc `id`.
- `GET /visits/{visitId}/history/{pastVisitId}`: chi tiết lịch sử thuộc cùng bệnh nhân. Không có lượt đang phụ trách hoặc ID lịch sử của người khác trả 404.

Khởi động khám chạy trong giao dịch `READ_COMMITTED`, khóa slot trước lượt, nên hai yêu cầu đồng thời không ghi đè `startedAt`. Các API không trả CCCD, thông tin đăng nhập hay số điện thoại. Không có migration mới.
