# M01.02 — Quản lý tài khoản nhân viên

Tất cả endpoint dưới đây yêu cầu session của ADMIN. Các thao tác ghi yêu cầu CSRF token theo `API_M01_01_AUTH.md`. Phản hồi tài khoản đặt `Cache-Control: no-store`; không trả mật khẩu hoặc mật khẩu băm. Các role nhân viên được phép là `RECEPTIONIST`, `DOCTOR`, `PHARMACIST`. Tài khoản ADMIN và PATIENT không xuất hiện trong danh sách hay chi tiết này.

## API

### `GET /api/v1/admin/staff-accounts`

Trả 200 với `PageResponse`. Hỗ trợ `page` từ 0, `size` từ 1 đến 100, `sort=id|username|fullName|role|active,asc|desc`. Mặc định `page=0&size=20`, sắp theo `id,asc`. Ví dụ:

```json
{"items":[{"id":12,"username":"staff_12","fullName":"Nhân viên A","phone":"<số liên hệ>","role":"RECEPTIONIST","active":true}],"page":0,"size":20,"totalItems":1,"totalPages":1}
```

Trang vượt phạm vi hoặc tham số không hợp lệ trả 400 `INVALID_REQUEST`.

### `GET /api/v1/admin/staff-accounts/{id}`

Trả 200 với DTO như một phần tử trong danh sách. ID không tồn tại, hoặc thuộc ADMIN/PATIENT, trả 404 `NOT_FOUND`.

### `POST /api/v1/admin/staff-accounts`

Tạo nhân viên và trả 201 với `Location: /api/v1/admin/staff-accounts/{id}`. Body:

```json
{"username":"staff_12","password":"<mật khẩu do quản trị viên nhập>","fullName":"Nhân viên A","phone":"<số liên hệ>","role":"RECEPTIONIST"}
```

`username` gồm 3–50 ký tự ASCII chữ, số, dấu chấm, gạch dưới hoặc gạch nối. `password` dài 6–128 ký tự và được băm trước khi lưu. `fullName` bắt buộc, tối đa 150 ký tự; `phone` bắt buộc, tối đa 20 ký tự. Với `role=DOCTOR`, cần thêm `specialtyId` là ID chuyên khoa đang hoạt động. Chuyên khoa phải được tạo trước; API quản lý danh mục chuyên khoa thuộc M02.01. Hồ sơ bác sĩ được tạo ở trạng thái chưa công khai trong cùng giao dịch với tài khoản. Các trường chuyên môn còn lại thuộc M02.02.

Sai trường bắt buộc hoặc sai role/chuyên khoa trả 400 `VALIDATION_ERROR` hoặc `INVALID_REQUEST`; username trùng trả 409 `USERNAME_TAKEN` hoặc `DATA_CONFLICT` khi hai yêu cầu đồng thời; chuyên khoa không tồn tại trả 404 `NOT_FOUND`, đã ngừng hoạt động trả 409 `SPECIALTY_INACTIVE`. Gửi lặp với cùng username không tạo bản ghi thứ hai. Lỗi khi tạo hồ sơ bác sĩ rollback cả tài khoản.

### `PATCH /api/v1/admin/staff-accounts/{id}`

Cho sửa `username`, `fullName`, `phone`, `role` bằng body chứa các trường cần thay đổi. Trả 200 với DTO mới. Mật khẩu được xử lý ở M01.04. Nếu đổi sang `DOCTOR`, phải gửi `specialtyId` đang hoạt động để tạo hồ sơ bác sĩ cùng giao dịch. Không gửi `specialtyId` khi giữ role hoặc đổi sang role khác. Không thể đổi role khi tài khoản đã có hồ sơ hoặc lịch sử nghiệp vụ; trả 409 `ROLE_LOCKED`. Đổi username hoặc role làm session cũ của tài khoản đó hết hiệu lực ở yêu cầu kế tiếp. Gửi lại cùng nội dung cho kết quả tài khoản hiện tại; username trùng trả 409.

### `POST /api/v1/admin/staff-accounts/{id}/lock`

Trả 204. Đặt `is_active=false`; session đang mở của tài khoản bị từ chối và hủy ở yêu cầu tiếp theo. Hồ sơ và lịch sử không bị xóa. Gửi lặp vẫn trả 204.

### `POST /api/v1/admin/staff-accounts/{id}/unlock`

Trả 204. Đặt `is_active=true`; không khôi phục session cũ, nhân viên phải đăng nhập lại. Gửi lặp vẫn trả 204.

Mọi endpoint: chưa đăng nhập trả 401 `UNAUTHENTICATED`; khác ADMIN trả 403 `FORBIDDEN`; thao tác ghi thiếu/sai CSRF token trả 403 `FORBIDDEN`. Lỗi ngoài dự kiến dùng hợp đồng `API_CONVENTIONS.md` và không trả stack trace.

## Dữ liệu và giao dịch

M01 sở hữu `user_account`. M02 sở hữu `specialty` và `doctor_profile`; M01 gọi service công khai của M02 khi tạo bác sĩ. Tạo hoặc đổi role sang DOCTOR chạy trong một transaction, ghi tài khoản trước rồi tạo hồ sơ. Đổi role khóa hàng tài khoản, sau đó kiểm tra các liên kết trực tiếp và trường audit trên các bảng hiện có; khóa hàng phối hợp với khóa FK trong MySQL để tránh nghiệp vụ mới chen vào giữa kiểm tra và đổi role. Không có migration mới trong gói này.
