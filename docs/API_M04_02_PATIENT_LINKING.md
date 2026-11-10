# M04.02 — Đối chiếu và liên kết hồ sơ cũ

Tiếp đón tra hồ sơ theo CCCD hoặc họ tên/ngày sinh bằng `GET /api/v1/reception/patients/registration-candidates` và đối chiếu giấy tờ với người bệnh. Phải chọn đúng hồ sơ cũ trước khi cấp mã; hệ thống không tự ghép chỉ vì CCCD hoặc họ tên trùng. Xem hợp đồng API, quyền và mã lỗi trong `API_M01_03_PATIENT_REGISTRATION.md`.

Nếu hồ sơ thiếu CCCD, `POST /api/v1/reception/patients/{id}/registration-verification` ghi CCCD lên hồ sơ cũ và cấp mã một lần. Nếu CCCD trên hồ sơ sai, nhân viên sửa **cùng hồ sơ** qua `PUT /api/v1/reception/patients/{id}` sau khi đối chiếu, rồi mới gọi API cấp mã. Sửa CCCD, họ tên hoặc ngày sinh làm mã xác minh trước đó hết hiệu lực. CCCD thuộc hồ sơ khác bị từ chối; không tạo hồ sơ thay thế để né xung đột.

Người bệnh gửi `POST /api/v1/public/auth/register` kèm `verificationToken` trong 15 phút. Tạo tài khoản và liên kết `patient.user_id` cùng một giao dịch. Khóa hồ sơ khi liên kết; chỉ một yêu cầu đồng thời thành công, yêu cầu còn lại rollback cả tài khoản. Hồ sơ đã liên kết không được cấp mã mới và không được gắn thêm tài khoản. Mã bệnh nhân và ID hồ sơ giữ nguyên, nên các lượt khám cũ tiếp tục trỏ đến hồ sơ này.

M04.05 kiểm tra việc hiển thị lượt cũ và lượt mới sau khi liên kết; tiêu chí đó còn mở cho đến khi có API lịch sử và kiểm thử xuyên luồng.
