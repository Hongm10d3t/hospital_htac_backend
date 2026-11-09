# M02.03 — Công khai và tra cứu bác sĩ

## Quản trị trạng thái công khai

ADMIN gọi `POST /api/v1/admin/doctor-profiles/{userId}/publish` hoặc `/unpublish`. Cả hai trả 200 với `DoctorProfileAdminResponse`; gọi lặp giữ nguyên trạng thái. Dùng session và CSRF như các API quản trị khác. Chỉ bật công khai khi tài khoản còn hoạt động với role DOCTOR và chuyên khoa còn hoạt động. Tắt công khai giữ nguyên hồ sơ và lịch sử.

## API công khai

- `GET /api/v1/public/doctors`: phân trang (`page`, `size`), lọc `specialtyId` dương, sắp xếp `userId|practiceStartYear|professionalTitle,asc|desc`. Mặc định trang 0, size 20, `userId,asc`.
- `GET /api/v1/public/doctors/{userId}`: chi tiết. Hồ sơ chưa công khai, tài khoản không còn hoạt động hoặc chuyên khoa đã ngừng hoạt động đều trả 404.

Không cần đăng nhập. Phản hồi là `DoctorPublicResponse`: `userId`, `fullName`, `specialtyId`, `specialtyName`, `avatarUrl`, `professionalTitle`, `position`, `practiceStartYear`, `introduction`, `clinicalExpertise`, `certifications`, `availableSlotsUrl`. Không có username, mật khẩu băm, số điện thoại hay dữ liệu tài khoản khác. Không cache phản hồi để thay đổi trạng thái hiển thị có hiệu lực ở yêu cầu tiếp theo.

`availableSlotsUrl` có dạng `/api/v1/public/clinic-slots?doctorId={userId}`. M03.03 đã triển khai endpoint này; kết quả chỉ gồm khung tương lai OPEN còn chỗ và hồ sơ bác sĩ vẫn công khai, hoạt động. URL có thể trả danh sách rỗng khi bác sĩ chưa có khung phù hợp. Không có migration mới.
