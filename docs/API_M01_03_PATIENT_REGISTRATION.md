# M01.03 — Đăng ký tài khoản bệnh nhân

Đăng ký dùng session và CSRF theo `API_M01_01_AUTH.md`. Bệnh nhân chưa có hồ sơ trùng định danh được tạo tài khoản PATIENT và hồ sơ mới trong một giao dịch. Tài khoản mới không tự đăng nhập; người dùng gọi API đăng nhập sau khi đăng ký thành công.

## `POST /api/v1/public/auth/register`

Không cần đăng nhập, cần CSRF token. Body:

```json
{
  "username": "patient_1",
  "password": "<mật khẩu do người dùng nhập>",
  "fullName": "<họ tên>",
  "phone": "<số liên hệ>",
  "citizenId": "<12 chữ số CCCD>",
  "dateOfBirth": "<YYYY-MM-DD>",
  "gender": "OTHER",
  "address": "<địa chỉ nếu có>"
}
```

`username` dài 3–50 ký tự ASCII chữ, số, dấu chấm, gạch dưới hoặc gạch nối. Mật khẩu dài 6–128 ký tự. Họ tên, điện thoại, CCCD, ngày sinh và giới tính là bắt buộc. `gender` nhận `MALE`, `FEMALE`, `OTHER`. `address` tùy chọn, tối đa 2000 ký tự. Trường `role` do client gửi thêm không được dùng; tài khoản luôn là PATIENT.

Nếu chưa có hồ sơ cùng CCCD và chưa có hồ sơ trùng họ tên/ngày sinh cần đối chiếu, trả 201, `Location: /api/v1/auth/me`, body:

```json
{"accountId":12,"patientId":34,"status":"ACTIVE"}
```

Nếu CCCD đã có hồ sơ hoặc họ tên/ngày sinh trùng hồ sơ chưa rõ CCCD, trả 409 `IDENTITY_REVIEW_REQUIRED` và **không tạo tài khoản**. Nếu hồ sơ đã liên kết, tiếp đón hướng dẫn người dùng đăng nhập hoặc khôi phục tài khoản; không tự tạo bản sao. Username trùng trả 409 `USERNAME_TAKEN`; gửi đồng thời cùng CCCD hoặc username chỉ một giao dịch được ghi, giao dịch còn lại trả 409 `DATA_CONFLICT` hoặc `IDENTITY_REVIEW_REQUIRED`. Dữ liệu sai trả 400 `VALIDATION_ERROR`/`INVALID_REQUEST`; thiếu CSRF trả 403 `FORBIDDEN`.

Khi hồ sơ cũ đã được tiếp đón xác minh, người dùng gửi lại body và thêm `verificationToken` là mã một lần do tiếp đón cấp. Mã dùng được trong 15 phút, chỉ cho đúng hồ sơ đã xác minh và bị xóa khi liên kết thành công. Hệ thống không lưu mã gốc, chỉ lưu SHA-256 của mã. Gửi lặp sau thành công không tạo tài khoản hoặc liên kết thứ hai.

## Luồng đối chiếu của tiếp đón

Tiếp đón **kiểm tra giấy tờ và thông tin với người đến trực tiếp trước khi cấp mã**. API chỉ hỗ trợ ghi nhận kết quả đối chiếu của nhân viên đã đăng nhập role RECEPTIONIST; `updated_by` ghi ID nhân viên.

### `GET /api/v1/reception/patients/registration-candidates`

Chỉ RECEPTIONIST. Tra theo `citizenId` **hoặc** cặp `fullName` và `dateOfBirth`. Hỗ trợ `page`/`size` theo quy ước chung, mặc định `0`/`20`, sắp theo `id`. Trả `PageResponse` gồm `id`, `patientCode`, `fullName`, `dateOfBirth`, `linked`; không trả CCCD hoặc dữ liệu khám. Tham số thiếu/sai tổ hợp trả 400 `INVALID_REQUEST`. Dùng ID hồ sơ đã đối chiếu cho bước cấp mã.

### `POST /api/v1/reception/patients/{id}/registration-verification`

Chỉ RECEPTIONIST; cần CSRF token. Body:

```json
{"citizenId":"<12 chữ số CCCD đã kiểm tra>","fullName":"<họ tên đã kiểm tra>","dateOfBirth":"<YYYY-MM-DD>"}
```

Hồ sơ phải chưa liên kết, họ tên/ngày sinh phải khớp, CCCD hiện có phải trùng hoặc còn trống. Nếu CCCD còn trống, cập nhật trên **hồ sơ cũ** trước khi cấp mã. Trả 200 với `token`, `expiresAt`, và `Cache-Control: no-store`; chỉ đưa mã cho đúng người đã được đối chiếu. Cấp mã mới làm mã cũ hết hiệu lực. Hồ sơ đã liên kết trả 409 `PATIENT_ALREADY_LINKED`; thông tin không khớp trả 409 `IDENTITY_MISMATCH`; ID không tồn tại trả 404. Không ghi token hoặc dữ liệu định danh vào log.

Các endpoint tiếp đón từ chối người chưa đăng nhập với 401 và role khác với 403. Dữ liệu hồ sơ và tài khoản dùng một transaction trong đăng ký; các ràng buộc UNIQUE ở MySQL là lớp bảo vệ cuối khi gửi trùng đồng thời.
