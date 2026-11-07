# Hợp đồng REST API nền tảng (N00.04)

## Quy ước chung

- Prefix `/api/v1`. Tài nguyên dùng danh từ số nhiều, ID dạng số nguyên trong path. API công khai ở `/api/v1/public/**`; các API còn lại yêu cầu quyền được cấu hình khi triển khai M01.
- Request và response dùng JSON UTF-8, tên trường camelCase. DTO riêng cho từng thao tác; không tuần tự hóa entity JPA.
- Thời điểm là ISO 8601 có offset, ví dụ `2026-11-08T00:30:00+07:00`; ngày là `YYYY-MM-DD`; số lượng/giá là số thập phân với tối đa ba/hai chữ số lẻ tương ứng.
- `GET` đọc, `POST` tạo/thực hiện hành động, `PUT` thay toàn bộ, `PATCH` sửa một phần. Thao tác trạng thái dùng endpoint hành động rõ nghĩa, ví dụ `POST /api/v1/stock-documents/{id}/post`.
- Danh sách dùng `page` từ 0, `size` từ 1 đến 100, `sort=field,asc|desc` trên danh sách trường cho phép; mặc định `page=0&size=20`. Bộ lọc thời gian dùng `from` gồm đầu và `to` loại cuối; trường không hỗ trợ hoặc giá trị ngoài phạm vi trả 400.
- Module truyền danh sách trường sort/filter được phép vào `PageQuery` và `FilterQuery`. Dùng `PageResponse.from` để từ chối trang vượt tổng số trang, trừ trang đầu của tập rỗng. Giá trị bộ lọc ngoài ngày phải được module kiểm tra theo kiểu dữ liệu và quyền của endpoint.
- Response danh sách có `{ "items": [], "page": 0, "size": 20, "totalItems": 0, "totalPages": 0 }`. Tạo thành công trả 201 và `Location`; hành động không có body trả 204. Yêu cầu gửi lặp phải có kết quả xác định trong hợp đồng từng endpoint.

## Lỗi

| HTTP | code chung | Khi nào |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR`, `INVALID_REQUEST` | DTO thiếu/sai định dạng, tham số không hợp lệ |
| 401 | `UNAUTHENTICATED` | Chưa đăng nhập hoặc phiên hết hạn |
| 403 | `FORBIDDEN` | Sai role hoặc sai đối tượng dữ liệu |
| 404 | `NOT_FOUND` | Không tìm thấy đối tượng có thể truy cập |
| 405 | `METHOD_NOT_ALLOWED` | Đường dẫn có nhưng phương thức HTTP không được hỗ trợ |
| 409 | mã nghiệp vụ, `DATA_CONFLICT` | Trạng thái không cho phép, trùng khóa, tranh chấp |
| 500 | `INTERNAL_ERROR` | Lỗi ngoài dự kiến; không trả thông tin nội bộ |

Body lỗi do `GlobalExceptionHandler` trả:

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Dữ liệu không hợp lệ",
  "timestamp": "2026-11-07T17:30:00Z",
  "path": "/api/v1/appointments",
  "fieldErrors": { "reason": "không được để trống" }
}
```

Mã lỗi nghiệp vụ dùng SCREAMING_SNAKE_CASE ổn định, ví dụ `SLOT_FULL`. Với tài nguyên nhạy cảm, trả 404 khi người gọi không được biết sự tồn tại của ID. Không ghi mật khẩu, CCCD, nội dung khám hay request body vào log lỗi.

## Luồng mẫu để các module áp dụng

API thực thi sẵn: `GET /api/v1/public/ping` trả 200 `{"status":"UP"}` khi tiến trình ứng dụng phục vụ HTTP. Đây là liveness, không xác nhận MySQL hoặc nghiệp vụ đã sẵn sàng.

Hợp đồng minh họa cho M03.03 (chưa triển khai): `POST /api/v1/appointments` với `{"slotId":12,"reason":"Khám đau đầu"}`. Patient được lấy từ phiên đăng nhập, không nhận `patientId` từ client. Thành công trả 201 với `id`, `appointmentCode`, `status:"PENDING"` và `Location`. Thiếu `reason` trả 400 `VALIDATION_ERROR`; không đăng nhập trả 401; role khác PATIENT trả 403; slot không tồn tại trả 404; slot hết chỗ hoặc đóng trả 409 `SLOT_FULL`/`SLOT_CLOSED`. Kiểm tra sức chứa thực hiện trong transaction sau khi khóa slot. Hành vi gửi lại yêu cầu phải được M03.03 chốt và kiểm thử trước khi mở endpoint.

Mỗi API mới ghi: đường dẫn/phương thức, role, quyền đối tượng, DTO, ví dụ thành công, các mã lỗi, quy tắc gửi lại, transaction và tác động dữ liệu.
