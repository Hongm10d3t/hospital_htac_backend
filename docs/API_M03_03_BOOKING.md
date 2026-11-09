# M03.03 — Tra cứu lịch và đặt khám cho bản thân

## Tra cứu khung còn nhận đăng ký

`GET /api/v1/public/clinic-slots` không cần đăng nhập. Bộ lọc tùy chọn: `date=YYYY-MM-DD`, `specialtyId`, `doctorId`, `page`, `size`, `sort=startAt,asc|desc`. Không truyền `date` thì tra trong toàn bộ thời hạn đặt trước. Ngày bệnh viện là `Asia/Ho_Chi_Minh`; ngày hiện tại và ngày cuối cùng của `hospital_config.advance_booking_days` đều hợp lệ. Chỉ trả slot tương lai `OPEN`, bác sĩ đã công khai và tài khoản còn hoạt động, phòng/chuyên khoa hoạt động, đúng chuyên khoa, còn ít nhất một suất theo công thức M03.02. Trang trả `PublicClinicSlotResponse`: ID, phòng/tên phòng, bác sĩ, chuyên khoa/tên, thời gian UTC, capacity và available; không có dữ liệu bệnh nhân hoặc liên hệ cá nhân. Đây là đích `availableSlotsUrl` của M02.03. Lọc ngày ngoài thời hạn trả 400.

## Đặt hẹn

`POST /api/v1/appointments` dành cho PATIENT đã đăng nhập, yêu cầu CSRF. Body `{"slotId":12,"reason":"Lý do khám"}`; không nhận `patientId` từ client. Patient được lấy từ hồ sơ M04 liên kết với ID tài khoản trong principal. Lý do không trắng, tối đa 3000 ký tự. Thành công trả 201 với `Location: /api/v1/appointments/{id}` và `AppointmentResponse` gồm `id`, `appointmentCode`, `slotId`, bác sĩ, phòng, thời gian khung, `status=PENDING`, các thời điểm xử lý khi có; không trả patient ID.

Trong giao dịch `READ_COMMITTED`, slot được khóa hàng trước khi kiểm tra trùng và sức chứa; quy tắc hẹn giữ suất dùng `SlotCapacityService` của M03.02. Slot phải còn tương lai, trong thời hạn đặt trước, `OPEN`, còn chỗ; bác sĩ công khai, tài khoản/chuyên khoa/phòng còn hoạt động. Hai người tranh suất cuối chỉ một người thành công. Gửi lặp cho cùng patient và slot, kể cả sau khi hẹn kết thúc, trả 409 `APPOINTMENT_ALREADY_EXISTS` và không tạo hẹn mới; muốn đặt lại cần chọn khung khác. Slot đóng trả `SLOT_CLOSED`, hết chỗ trả `SLOT_FULL`, ngoài thời hạn trả `SLOT_OUTSIDE_BOOKING_WINDOW`; tài khoản chưa liên kết hồ sơ trả `PATIENT_PROFILE_REQUIRED`. Slot thiếu trả 404, sai role trả 403, chưa đăng nhập trả 401.

Hẹn chưa tiếp nhận không tạo `medical_visit`; chỉ lưu `appointment`. Không có migration mới. Quy tắc hoàn tất và API lịch cá nhân nằm trong M03.04.
