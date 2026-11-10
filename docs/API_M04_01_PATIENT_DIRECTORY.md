# M04.01 — Hồ sơ bệnh nhân

M04 sở hữu entity và repository ghi của `patient`; không thay đổi schema. Hồ sơ bệnh nhân độc lập với tài khoản, số điện thoại nằm ở `user_account` và không có trong DTO hồ sơ.

## Tiếp đón

Các API `/api/v1/reception/patients` chỉ cho RECEPTIONIST, dùng session và CSRF cho thao tác ghi.

- `GET` yêu cầu đúng một cách tìm: `patientCode`, `citizenId` 12 chữ số, hoặc cặp `fullName` và `dateOfBirth`. Hỗ trợ `page`, `size`, `sort=id,asc|desc`. Không mở danh sách toàn bộ hồ sơ khi thiếu bộ lọc.
- `GET /{id}` trả chi tiết hành chính, 404 nếu không có.
- `POST` body `citizenId` tùy chọn, `fullName`, `dateOfBirth`, `gender`, `address` tùy chọn. Bệnh viện cấp `patientCode` duy nhất; `userId` luôn NULL ở luồng tạo trực tiếp. Trả 201 và `Location` của hồ sơ mới. Người tiếp đón cần tìm hồ sơ đã có trước khi tạo. CCCD trùng trả 409 `PATIENT_CITIZEN_ID_TAKEN`; ràng buộc UNIQUE của MySQL bảo vệ khi gửi đồng thời.
- `PUT /{id}` thay toàn bộ thông tin định danh và địa chỉ; không nhận `patientCode` hoặc `userId` từ client. Bản ghi được khóa trước khi cập nhật. Khi họ tên/ngày sinh/CCCD thay đổi, mã xác minh liên kết chưa sử dụng bị vô hiệu hóa; liên kết tài khoản và lịch sử vẫn giữ nguyên. Không xóa cứng hồ sơ.

Họ tên không trắng, tối đa 150 ký tự; ngày sinh không sau ngày hiện tại; giới tính `MALE|FEMALE|OTHER`; địa chỉ tối đa 2000 ký tự. CCCD có thể NULL với người đến trực tiếp, nếu có phải đúng 12 chữ số. DTO gồm `id`, `patientCode`, `userId`, `citizenId`, `fullName`, `dateOfBirth`, `gender`, `address`; không có mật khẩu, phone hoặc nội dung khám.

## Bệnh nhân

`GET /api/v1/patients/me` chỉ cho PATIENT đang hoạt động, trả đúng hồ sơ liên kết với ID tài khoản trong principal. Không nhận patient ID từ URL hoặc query. Chưa có hồ sơ trả 404. Phản hồi `Cache-Control: no-store`.
