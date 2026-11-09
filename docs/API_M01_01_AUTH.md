# M01.01 — Xác thực bằng session

## Kết nối từ trình duyệt

Frontend gọi API qua cùng origin với trang web. Khi phát triển trên cổng khác, cấu hình proxy của frontend để trình duyệt vẫn gọi cùng origin. Backend không bật CORS cho origin ngoài. Cookie phiên `JSESSIONID` là `HttpOnly`, `SameSite=Lax`; profile `deploy` bật `Secure` và cần HTTPS.

Với mọi `POST`, `PUT`, `PATCH`, `DELETE`, frontend gửi CSRF token trong header do endpoint dưới đây chỉ định. Gọi lấy token khi mở ứng dụng và **gọi lại sau khi đăng nhập hoặc đăng xuất**, vì token cũ bị xóa. Không lưu token hoặc thông tin phiên vào localStorage. Các phản hồi xác thực và CSRF đặt `Cache-Control: no-store`.

## API

### `GET /api/v1/public/csrf`

Không cần đăng nhập. Trả 200 và tạo session nếu chưa có. Ví dụ phản hồi:

```json
{"headerName":"X-CSRF-TOKEN","token":"<CSRF token của phiên>"}
```

Gửi lại yêu cầu tạo hoặc lấy token của phiên hiện tại; không thay đổi dữ liệu nghiệp vụ.

### `POST /api/v1/public/auth/login`

Không cần đăng nhập, nhưng phải gửi cookie phiên và CSRF header vừa lấy. Body JSON:

```json
{"username":"<tên đăng nhập>","password":"<mật khẩu nhập tại giao diện>"}
```

Chỉ tài khoản đang hoạt động, có mật khẩu băm hợp lệ mới được đăng nhập. Thành công trả 200, đổi ID phiên và trả DTO tài khoản của chính người đăng nhập:

```json
{"id":1,"username":"<tên đăng nhập>","fullName":"<tên hiển thị>","phone":"<số liên hệ>","role":"ADMIN"}
```

Role có thể là `ADMIN`, `RECEPTIONIST`, `DOCTOR`, `PHARMACIST`, `PATIENT`. Không trả `passwordHash` hay thông tin định danh patient. Sai username, mật khẩu hoặc tài khoản bị khóa đều trả 401 `UNAUTHENTICATED` cùng thông điệp chung. Body thiếu hoặc trống trả 400 `VALIDATION_ERROR`; thiếu/sai CSRF token trả 403 `FORBIDDEN`. Đăng nhập lại bằng thông tin hợp lệ tạo phiên xác thực mới; không ghi bảng nghiệp vụ.

### `GET /api/v1/auth/me`

Yêu cầu session đã đăng nhập. Trả 200 với DTO tài khoản như phản hồi đăng nhập, lấy thông tin hiện tại từ `user_account`. Chưa đăng nhập, phiên hết hạn, tài khoản đã bị khóa/xóa hoặc role đã thay đổi thì trả 401 `UNAUTHENTICATED`. Kiểm tra trạng thái tài khoản tại mỗi yêu cầu có xác thực; khi không còn hợp lệ, phiên bị hủy.

### `POST /api/v1/auth/logout`

Yêu cầu CSRF header. Trả 204, hủy session, xóa cookie và thông tin xác thực; không có body. Gửi lại khi đã đăng xuất vẫn trả 204 nếu có CSRF token hợp lệ của phiên mới. Thiếu/sai token trả 403 `FORBIDDEN`.

## Phân quyền hiện tại

Chỉ `/api/v1/public/**` được gọi ẩn danh. Cả năm role được gọi `/api/v1/auth/me`; các route chưa cấp quyền bị chặn mặc định. API quản trị nhân viên, đăng ký bệnh nhân và thông tin cá nhân bổ sung ở M01.02–M01.04 được mô tả trong tài liệu gói tương ứng. Chức vụ trong hồ sơ bác sĩ không làm thay đổi role.

Lỗi tuân theo `API_CONVENTIONS.md`. Đăng nhập/đăng xuất không ghi dữ liệu nghiệp vụ; bootstrap ADMIN là thao tác riêng ngoài API. Dữ liệu audit lấy `userId` từ principal, không lấy từ request body.
