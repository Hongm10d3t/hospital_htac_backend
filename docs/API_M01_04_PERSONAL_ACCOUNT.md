# M01.04 — Thông tin tài khoản và mật khẩu

Các API dùng session và CSRF theo `API_M01_01_AUTH.md`. Tên hiển thị và số điện thoại ở `user_account` là thông tin tài khoản; sửa chúng không thay họ tên, ngày sinh, CCCD hoặc lịch sử trên hồ sơ `patient`.

## `PATCH /api/v1/auth/me`

Mọi role đã đăng nhập được sửa **chính tài khoản của mình**. Body gồm một hoặc hai trường `fullName`, `phone`; không nhận `id`, role, username hoặc thông tin patient. Ví dụ:

```json
{"fullName":"<tên hiển thị mới>","phone":"<số liên hệ mới>"}
```

Trả 200 với DTO như `GET /api/v1/auth/me`, `Cache-Control: no-store`. Tên hiển thị không trống và tối đa 150 ký tự; điện thoại không trống và tối đa 20 ký tự. Body rỗng hoặc chuỗi chỉ có khoảng trắng trả 400. `updated_by` và `updated_at` trên `user_account` ghi người/thời điểm sửa.

## `POST /api/v1/auth/change-password`

Mọi role đã đăng nhập được đổi mật khẩu của chính mình. Body:

```json
{"currentPassword":"<mật khẩu hiện tại>","newPassword":"<mật khẩu mới>"}
```

Mật khẩu hiện tại phải khớp hash đã lưu. Mật khẩu mới dài 6–128 ký tự và khác mật khẩu hiện tại. Sai điều kiện trả 400 `INVALID_REQUEST`; thành công trả 204 không có body. Hash được cập nhật, `credential_version` tăng, phiên hiện tại bị hủy và các phiên cũ bị từ chối ở yêu cầu tiếp theo. Người dùng đăng nhập lại bằng mật khẩu mới. API và log không trả hoặc ghi mật khẩu gốc.

## `POST /api/v1/admin/accounts/{id}/password-reset`

Chỉ ADMIN. Trong bản demo, ADMIN phải **đối chiếu danh tính ngoài hệ thống trước khi gọi API** và ghi phương thức đã dùng. Body:

```json
{"newPassword":"<mật khẩu mới đã thống nhất ngoài hệ thống>","verifiedVia":"IN_PERSON"}
```

`verifiedVia` nhận `IN_PERSON` hoặc `PHONE_CALLBACK`. Mật khẩu mới theo cùng điều kiện đổi mật khẩu. Không cho đặt lại mật khẩu của tài khoản ADMIN bằng API này. Thành công trả 204 không có body; sai dữ liệu trả 400, ID không tồn tại trả 404, nhắm ADMIN trả 409 `ADMIN_RESET_FORBIDDEN`. Bản ghi `account_password_reset_audit` lưu ID tài khoản đích, ID ADMIN, phương thức đối chiếu và thời điểm; không lưu mật khẩu gốc. `updated_by`/`updated_at` trên tài khoản đích cũng được cập nhật. Phiên cũ của tài khoản đích bị từ chối ở yêu cầu tiếp theo.

Mọi thao tác ghi cần CSRF token. Chưa đăng nhập trả 401; sai role tại API ADMIN trả 403. Gửi lặp yêu cầu đổi/đặt lại cùng mật khẩu sau khi đã thành công trả 400 vì mật khẩu mới phải khác hiện tại. Các thay đổi tài khoản và audit đặt lại mật khẩu ở trong cùng transaction.
