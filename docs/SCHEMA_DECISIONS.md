# Quyết định schema và nghiệp vụ nền tảng (N00.02)

Ngày 07/11/2026. Tài liệu này chốt cách triển khai cho bản backend hiện tại từ `PHAM_VI_DU_AN.md` và `DATABASE.md`. Khi thay đổi quyết định, tạo migration mới và cập nhật hợp đồng module liên quan.

## Ánh xạ 18 bảng nghiệp vụ nền

| Bảng | Module ghi | Khóa và ràng buộc chính |
| --- | --- | --- |
| `user_account` | M01 | PK `id`, UQ `username`, role cố định |
| `patient` | M04 | PK, UQ `patient_code`, `user_id`, `citizen_id` khi có |
| `doctor_profile` | M02 | PK/FK `user_id` đến `user_account.id`; FK `specialty_id` đến `specialty.id` |
| `specialty` | M02 | PK, UQ `code` |
| `clinic_room` | M02 | PK, UQ `code`, FK `specialty_id` |
| `clinic_slot` | M03 | PK, FK `room_id`, `doctor_id` đến `doctor_profile.user_id`; `end_at > start_at`, capacity dương |
| `appointment` | M03 | PK, UQ `appointment_code`, FK patient/slot/confirmer |
| `medical_visit` | M05 | PK, UQ `visit_code`, `appointment_id`, `(room_id, queue_date, queue_number)` |
| `prescription` | M05 | PK, UQ `prescription_code`, FK visit và `doctor_profile.user_id` |
| `prescription_item` | M05 | PK, UQ `(prescription_id, medicine_id)`, lượng dương |
| `medicine` | M06 | PK, UQ `code`, lượng tồn/ngày chờ không âm |
| `medicine_batch` | M06 | PK, UQ `(medicine_id, batch_number)`, FK medicine |
| `supplier` | M06 | PK, UQ `code` |
| `stock_document` | M06 | PK, UQ `document_code`, FK supplier/prescription/request/poster |
| `stock_document_item` | M06 | PK, FK document/batch/prescription item/source item |
| `purchase_request` | M07 | PK, UQ `request_code`, FK supplier/reviewer |
| `purchase_request_item` | M07 | PK, UQ `(request_id, medicine_id)`, lượng dương |
| `hospital_config` | M01 | PK chỉ nhận `id=1`; `advance_booking_days > 0` |

Migration V3 bổ sung `credential_version` trên `user_account` và bảng audit bảo mật `account_password_reset_audit` do M01 sở hữu. Bảng audit này nằm ngoài 18 bảng nghiệp vụ nền; lưu người đặt lại, tài khoản đích, phương thức đối chiếu và thời điểm, không lưu mật khẩu. Thay đổi mật khẩu tăng phiên bản để vô hiệu hóa session cũ.

Mỗi bảng có `created_at`, `updated_at` bắt buộc và `created_by`, `updated_by` cho phép NULL, FK đến `user_account.id`. Không xóa cứng tài khoản hoặc dữ liệu nghiệp vụ đã được tham chiếu. Cột `medical_visit.room_id` và `queue_date` là dữ liệu chốt lúc tiếp nhận để MySQL bảo vệ số thứ tự duy nhất theo phòng/ngày; M04/M05 phải đối chiếu `room_id` với `clinic_slot.room_id`. Số thứ tự VOIDED không tái sử dụng.

## Kiểu dữ liệu và ngày nghiệp vụ

| Nguồn tài liệu | MySQL | Java/API |
| --- | --- | --- |
| `bigint` | `BIGINT` | `Long` / số nguyên JSON |
| `varchar(n)`, `text` | `VARCHAR(n)`, `TEXT` | `String` |
| `boolean` | `BOOLEAN` (TINYINT(1)) | `boolean`/`Boolean` |
| `date` | `DATE` | `LocalDate`, `YYYY-MM-DD` |
| `timestamptz` | `DATETIME(6)` lưu UTC | `Instant`, ISO 8601 có offset |
| `numeric(14,3)` | `DECIMAL(14,3)` | `BigDecimal`, đơn vị cơ sở |
| `numeric(14,2)` | `DECIMAL(14,2)` | `BigDecimal`, giá trên đơn vị cơ sở |
| `integer`, `smallint` | `INT`, `SMALLINT` | `Integer`, `Short` |

Driver đặt session timezone UTC; Hibernate đặt JDBC timezone UTC. Mọi thời điểm gửi qua API có offset và được chuyển về UTC trước khi lưu. Bệnh viện dùng `Asia/Ho_Chi_Minh` để tính ngày xếp hàng, lịch, hạn dùng, báo cáo và dataset. Ví dụ `2026-11-07T17:30:00Z` là `2026-11-08 00:30` tại bệnh viện, nên `queue_date=2026-11-08`. Khoảng lọc một ngày là `[00:00 ngày đó, 00:00 ngày kế tiếp)` theo múi giờ bệnh viện rồi chuyển sang UTC. Lô có `expiry_date` bằng ngày cấp được xem là hết hạn từ đầu ngày đó; chỉ cấp khi `expiry_date` sau ngày nghiệp vụ.

Chuỗi trạng thái và lý do được giữ đúng tên trong `DATABASE.md`; database dùng `CHECK` cho tập giá trị và điều kiện cùng hàng, service kiểm tra chuyển trạng thái và quan hệ nhiều bảng. `NULL` chỉ dùng tại các cột được tài liệu cho phép và trường audit của tác vụ hệ thống. Không dùng `NULL` để biểu diễn số lượng 0. Index tập trung vào khóa tra cứu lịch, lượt, đơn, lô, phiếu và đề xuất; thêm index theo truy vấn đo được về sau.

## Quy tắc cần phối hợp giữa module

- Tài khoản đã có patient, doctor profile hoặc lịch sử nghiệp vụ không đổi role. Khóa/mở tài khoản không xóa lịch sử. M01 xử lý phiên đăng nhập đã bị khóa bằng cách kiểm tra `is_active` ở mỗi yêu cầu có xác thực.
- Không tự liên kết hồ sơ cũ chỉ vì CCCD nhập khớp. M04 yêu cầu tiếp đón xác minh thêm định danh trước khi liên kết; nếu chưa đủ chứng cứ, đăng ký trả trạng thái cần đối chiếu. Tạo tài khoản và liên kết/tạo patient phải cùng transaction.
- M01.03 dùng mã xác minh một lần do RECEPTIONIST cấp sau khi đối chiếu trực tiếp. Migration V2 thêm hash và hạn mã vào `patient`; mã gốc chỉ trả một lần, hết hạn sau 15 phút và xóa khi liên kết. Yêu cầu đăng ký cần đối chiếu trả 409 mà không tạo tài khoản. Hồ sơ thiếu CCCD được tiếp đón bổ sung trên bản ghi cũ trước khi cấp mã.
- DOCTOR chỉ đọc lịch sử bệnh nhân đang có lượt được phân công; PATIENT chỉ đọc hồ sơ đã gắn tài khoản. Không mở API tìm lịch sử toàn viện theo role đơn thuần.
- Phiếu `POSTED` là bất biến; `CANCELLED` chỉ áp dụng DRAFT. Sai sót sau ghi sổ được sửa bằng phiếu nghiệp vụ mới có tham chiếu/ghi chú, không đổi phiếu cũ. M06 phải công bố quy tắc tác động tồn cho từng type trước khi ghi sổ.
- Thuốc bệnh nhân trả vào `QUARANTINE`; lượng trả không mở thêm quyền cấp theo đơn cũ. Nếu cần cấp thay thế, bác sĩ lập đơn bổ sung. Tổng DISPENSE của dòng đơn không vượt lượng kê, kể cả khi có trả.
- Lượt `NOT_COMPLETED` vẫn đếm là đã đến; `VOIDED` không đếm. Hẹn PENDING/CONFIRMED giữ suất; tiếp nhận người có hẹn chuyển từ suất giữ sang lượt, không tính hai lần. Khóa `clinic_slot` khi thay đổi sức chứa.
- `unfilled_quantity` là hiện trạng gần nhất; `dispensing_checked_at=NULL` nghĩa là chưa kiểm tra. Chỉ `DISPENSE` `POSTED` tạo lượng thuốc thực cấp cho báo cáo/ML.

Độ nhất quán đa hàng như bác sĩ đúng chuyên khoa phòng, lịch không chồng, patient của hẹn đúng patient của lượt, số dư kho không âm và lượng nhận không vượt duyệt được kiểm tra trong transaction dưới khóa điều phối; FK/CHECK/UNIQUE không thể thay thế các kiểm tra này.
