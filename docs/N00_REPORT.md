# Kết quả triển khai N00

Ngày kiểm tra: 07/11/2026. Database đích do người dùng chọn: `htac` trên MySQL localhost:3306. Không ghi thông tin xác thực vào Git.

| Gói | Thành phần đã thêm | Vai trò và bằng chứng |
| --- | --- | --- |
| N00.01 | `AGENTS.md`, `BACKEND_RULES.md`, package M01–M09, sửa Maven Wrapper | Quy định cách nhận gói, quyền sở hữu bảng, cách kiểm thử; `mvnw.cmd package` thành công với Java 25 và cache Maven được chỉ định. |
| N00.02 | `SCHEMA_DECISIONS.md` | Ánh xạ đủ 18 bảng, FK bác sĩ, UTC/MySQL, ngày bệnh viện, số thập phân, dữ liệu NULL và quy tắc liên module. |
| N00.03 | `V1__initial_schema.sql` | Flyway tạo 18 bảng nghiệp vụ và bảng lịch sử trong `htac`; phiên bản 1 thành công. Khởi động lại không áp dụng migration lần hai. Kiểm thử MySQL xác nhận unique, FK và rollback. |
| N00.04 | `API_CONVENTIONS.md`, DTO phản hồi, phân trang, xử lý lỗi, ping công khai, bảo mật mặc định | API có dạng lỗi thống nhất; thử HTTP thực tế: ping 200, chưa đăng nhập 401, đường dẫn công khai không tồn tại 404. Kiểm thử MockMvc xác nhận 403, validation và xung đột nghiệp vụ. |
| N00.05 | Profile `local`, `test`, `deploy`; cấu hình JDBC/Flyway/JPA; `.env.example`; audit JPA | Local đọc `.env.local` bị Git bỏ qua; deploy dùng biến môi trường và cookie Secure. Kiểm thử MySQL xác nhận rollback khi lỗi giữa giao dịch, audit ghi người tạo/người sửa và thời điểm. |

## Kiểm tra đã chạy

- `mvnw.cmd package` và `mvn package` thành công. Có 7 kiểm thử unit/API đạt ở bản mã mới nhất; 6 kiểm thử MySQL đã đạt ở lượt chạy trên `htac` trước thay đổi tiện ích lọc/phân trang. Dữ liệu kiểm thử được rollback.
- `htac` có 19 bảng (18 bảng nghiệp vụ và `flyway_schema_history`), migration V1 ở trạng thái thành công. Sau kiểm thử, `user_account`, `patient`, `medical_visit` vẫn không có bản ghi.
- Database tạm `htac_hospital_n00_test` đã được xóa theo yêu cầu; không còn schema này trên MySQL.
- Ứng dụng khởi động được ở profile `local` với kết nối đọc từ `.env.local`. Mật khẩu không nằm trong `.env.example` hoặc tệp source.

## Tiêu chí chưa thể nghiệm thu ở thời điểm này

- N00.03 yêu cầu diễn tập **migration tiếp theo trên bản sao có dữ liệu**. Hiện mới có V1, và người dùng đã yêu cầu xóa database kiểm thử. Khi có thay đổi schema thật, tạo V2 và diễn tập trên bản sao dữ liệu trước khi đưa vào `htac`; không tạo V2 chỉ để đánh dấu checklist.

Các module M01–M09 vẫn chỉ có cấu trúc package. Schema và thành phần dùng chung là nền tảng cho phần nghiệp vụ, chưa phải API đăng nhập, đặt hẹn, khám hoặc kho thuốc.
