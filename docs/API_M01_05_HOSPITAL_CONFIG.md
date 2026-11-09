# M01.05 — Thông tin và cấu hình bệnh viện

Hệ thống quản lý một cơ sở. Bảng `hospital_config` có đúng một ID hợp lệ là `1`; khi chưa có cấu hình, ADMIN phải tạo bằng API dưới đây. Không có migration mới trong gói này.

## `GET /api/v1/public/hospital`

Không cần đăng nhập. Trả 200 với thông tin được phép công khai và `Cache-Control: no-store`:

```json
{
  "name": "<tên cơ sở>",
  "address": "<địa chỉ>",
  "phone": "<hotline>",
  "email": "<email nếu có>",
  "logoUrl": "https://example.invalid/logo.png",
  "description": "<giới thiệu nếu có>",
  "advanceBookingDays": 14
}
```

Chưa có cấu hình trả 404 `NOT_FOUND`.

## `PUT /api/v1/admin/hospital-config`

Chỉ ADMIN, yêu cầu CSRF token. Body gồm toàn bộ trường như phản hồi GET, trong đó `name`, `address`, `phone`, `advanceBookingDays` bắt buộc; `email`, `logoUrl`, `description` tùy chọn. Tên tối đa 200 ký tự; địa chỉ tối đa 2000; điện thoại tối đa 20; email đúng định dạng và tối đa 150; URL logo tối đa 500 và phải dùng HTTPS; giới thiệu tối đa 10000. `advanceBookingDays` phải là số nguyên lớn hơn 0.

Lần ghi đầu trả 201, `Location: /api/v1/public/hospital`; các lần cập nhật trả 200. Body là cấu hình mới. Gửi lại cùng nội dung giữ một bản ghi với ID `1`. Hai yêu cầu tạo đầu tiên đồng thời chỉ có tối đa một bản ghi; yêu cầu thua tranh chấp có thể nhận 409 `DATA_CONFLICT`. Dữ liệu sai trả 400 `VALIDATION_ERROR` hoặc `INVALID_REQUEST`. Chưa đăng nhập trả 401; khác ADMIN trả 403. Audit `created_by`/`updated_by` ghi ADMIN thao tác.

## Hợp đồng service cho M03

`HospitalConfigService.maxAdvanceBookingDays()` đọc giá trị hiện hành từ database, không lưu cache. `isWithinAdvanceWindow(requestedDate, hospitalDate)` trả `true` nếu ngày yêu cầu nằm từ ngày nghiệp vụ đến ngày nghiệp vụ cộng số ngày đặt trước, tính cả hai đầu. M03 cung cấp `hospitalDate` theo múi giờ `Asia/Ho_Chi_Minh` khi kiểm tra một yêu cầu đặt mới. Thay đổi cấu hình ảnh hưởng ngay lần đọc tiếp theo. Khi chưa cấu hình, service trả 409 `HOSPITAL_CONFIG_MISSING`.
