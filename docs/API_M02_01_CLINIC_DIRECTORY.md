# M02.01 — Danh mục chuyên khoa và phòng khám

Các API quản trị dưới đây chỉ dành cho ADMIN, dùng session và CSRF theo `API_M01_01_AUTH.md`. Không có API xóa cứng; bản ghi ngừng hoạt động vẫn được giữ để tham chiếu lịch sử. M02 sở hữu entity và repository của `specialty`, `clinic_room`; không có migration mới.

## Chuyên khoa

### `GET /api/v1/admin/specialties`

Trả `PageResponse<SpecialtyResponse>` gồm `id`, `code`, `name`, `active`. Lọc tùy chọn `active=true|false`; phân trang `page`, `size`, sắp xếp `sort=id|code|name|active,asc|desc`. Mặc định trang 0, size 20, `id,asc`.

### `GET /api/v1/admin/specialties/{id}`

Trả 200 với DTO kể cả khi chuyên khoa đã ngừng hoạt động; ID không tồn tại trả 404.

### `POST /api/v1/admin/specialties`

Body `{"code":"CARD","name":"<tên chuyên khoa>"}`. `code` dài 1–20 ký tự ASCII chữ, số, `.`, `_`, `-`; `name` không trống, tối đa 100 ký tự. Trả 201 với `Location` trỏ đến chi tiết. Mã trùng trả 409 `SPECIALTY_CODE_TAKEN`, hoặc `DATA_CONFLICT` nếu hai yêu cầu cạnh tranh. Gửi lại cùng mã không tạo bản ghi thứ hai.

### `PUT /api/v1/admin/specialties/{id}`

Thay toàn bộ `code`, `name` theo cùng điều kiện tạo. Trả 200; mã trùng trả 409. Không thay `active` qua body này.

### `POST /api/v1/admin/specialties/{id}/activate` và `/deactivate`

Trả 204; gọi lặp giữ nguyên trạng thái. Ngừng hoạt động không xóa phòng hoặc hồ sơ bác sĩ đã gắn chuyên khoa, nhưng các service tạo hồ sơ/phòng mới và tra cứu phân công cho lịch mới từ chối chuyên khoa ngừng hoạt động.

## Phòng khám

### `GET /api/v1/admin/clinic-rooms`

Trả `PageResponse<ClinicRoomResponse>` gồm `id`, `specialtyId`, `code`, `name`, `location`, `active`. Lọc tùy chọn `specialtyId` dương và `active`; sắp xếp `sort=id|code|name|specialtyId|active,asc|desc`. Quy tắc phân trang như trên.

### `GET /api/v1/admin/clinic-rooms/{id}`

Trả 200 kể cả khi phòng đã ngừng hoạt động; ID không tồn tại trả 404.

### `POST /api/v1/admin/clinic-rooms`

Body `{"specialtyId":1,"code":"R01","name":"<tên phòng>","location":"<vị trí nếu có>"}`. Chuyên khoa phải tồn tại và đang hoạt động. `code` như chuyên khoa; tên không trống, tối đa 100 ký tự; vị trí tùy chọn, tối đa 200 ký tự. Trả 201 với `Location`. Chuyên khoa thiếu trả 404, ngừng hoạt động trả 409 `SPECIALTY_INACTIVE`, mã phòng trùng trả 409 `ROOM_CODE_TAKEN` hoặc `DATA_CONFLICT` khi cạnh tranh.

### `PUT /api/v1/admin/clinic-rooms/{id}`

Thay toàn bộ `specialtyId`, `code`, `name`, `location`. Có thể xóa vị trí bằng `null`. Đổi chuyên khoa khi phòng **chưa có** `clinic_slot` và chuyên khoa mới đang hoạt động; nếu đã có lịch trả 409 `ROOM_SPECIALTY_LOCKED`. Phòng bị khóa hàng trong giao dịch kiểm tra và cập nhật. Giữ nguyên chuyên khoa cho phép sửa các trường khác dù chuyên khoa cũ đã ngừng hoạt động, để bảo trì dữ liệu lịch sử.

### `POST /api/v1/admin/clinic-rooms/{id}/activate` và `/deactivate`

Trả 204, gửi lặp giữ nguyên trạng thái. Chỉ bật lại phòng nếu chuyên khoa hiện tại đang hoạt động. Tắt phòng giữ nguyên lịch sử. `ClinicDirectoryLookupService.requireActiveRoom` là hợp đồng đọc cho M03: kiểm tra cả phòng và chuyên khoa còn hoạt động trước khi lập lịch mới.

Tất cả endpoint quản trị: chưa đăng nhập trả 401; sai role trả 403; thao tác ghi thiếu/sai CSRF trả 403; DTO sai trả 400. API không trả entity JPA. Việc kiểm tra trong M03 trên lịch thật sẽ được nghiệm thu khi M03 có API lập lịch.
