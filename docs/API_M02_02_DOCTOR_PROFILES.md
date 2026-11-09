# M02.02 — Hồ sơ chuyên môn bác sĩ

Các API dưới `/api/v1/admin/doctor-profiles` dành cho ADMIN, dùng session và CSRF như `API_M01_01_AUTH.md`. Không có migration mới: `doctor_profile` và khóa ngoại đã có trong V1.

## Quản trị hồ sơ

- `GET /api/v1/admin/doctor-profiles`: danh sách phân trang, lọc tùy chọn `specialtyId`, sắp xếp `userId|specialtyId|practiceStartYear,asc|desc`.
- `GET /api/v1/admin/doctor-profiles/{userId}`: chi tiết, kể cả hồ sơ chưa công khai; không tìm thấy trả 404.
- `POST /api/v1/admin/doctor-profiles`: tạo hồ sơ cho tài khoản `DOCTOR` đã tồn tại nhưng chưa có hồ sơ. Body có `userId`, `specialtyId` bắt buộc; `avatarUrl`, `professionalTitle`, `position`, `practiceStartYear`, `introduction`, `clinicalExpertise`, `certifications` tùy chọn. Trả 201 với `Location`. Luồng M01.02 đã tạo hồ sơ tối thiểu khi tạo tài khoản DOCTOR; dùng `PUT` để bổ sung hồ sơ đó.
- `PUT /api/v1/admin/doctor-profiles/{userId}`: thay toàn bộ trường hồ sơ, ngoại trừ `userId` và `isPublic`. Trường tùy chọn bỏ trống được xóa. Việc bật/tắt công khai thuộc M02.03.

`avatarUrl` nếu có phải là HTTPS URL hợp lệ, không chứa user info, tối đa 500 ký tự. Chức danh tối đa 100, chức vụ tối đa 150; ba trường văn bản dài tối đa 3000 ký tự mỗi trường. Năm hành nghề từ 1900 đến năm hiện tại theo múi giờ bệnh viện. Chuyên khoa mới phải tồn tại và đang hoạt động. Khi bác sĩ đã có bất kỳ `clinic_slot` nào, đổi chuyên khoa trả 409 `DOCTOR_SPECIALTY_LOCKED`; vẫn được sửa các trường mô tả khác. Hồ sơ được khóa hàng trong giao dịch cập nhật.

Một tài khoản có tối đa một hồ sơ qua PK/FK `user_id`. Tài khoản không mang role DOCTOR trả 409 `DOCTOR_ACCOUNT_REQUIRED`; tạo lặp trả 409 `DOCTOR_PROFILE_EXISTS`. `position` chỉ là dữ liệu hiển thị, không sửa role. DTO quản trị không chứa username, mật khẩu hoặc số điện thoại. `DoctorAssignmentLookupService.requireActiveDoctor` cung cấp hợp đồng kiểm tra DOCTOR, tài khoản và chuyên khoa hoạt động cho M03.

Thiếu đăng nhập trả 401, sai role trả 403; thao tác ghi thiếu/sai CSRF trả 403; body sai trả 400.
