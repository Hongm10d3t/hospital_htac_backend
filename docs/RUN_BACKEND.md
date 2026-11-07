# Chạy backend nền tảng

## Yêu cầu

Java 25, MySQL 8.0.16+ và cổng kết nối tới database riêng của dự án. Maven được cung cấp qua Maven Wrapper. Ứng dụng cần quyền tạo/sửa bảng để Flyway chạy lần đầu; sau khi migrate có thể tách quyền runtime ở giai đoạn triển khai.

Tạo database rỗng với charset `utf8mb4` trước lần chạy đầu. Không chạy migration thử trên database đang có dữ liệu chưa sao lưu. File `.env.example` ghi tên biến, không chứa mật khẩu. Profile `local` tự đọc `.env.local` tại thư mục dự án nếu có; tệp này bị Git bỏ qua. Có thể dùng biến môi trường trong terminal hoặc cấu hình chạy IDE thay thế:

| Biến | Mô tả |
| --- | --- |
| `DB_URL` | JDBC URL đầy đủ; ưu tiên nếu có |
| `DB_HOST`, `DB_PORT`, `DB_NAME` | Dùng khi không đặt `DB_URL`; mặc định localhost, 3306, htac |
| `DB_USERNAME`, `DB_PASSWORD` | Tài khoản MySQL và mật khẩu; không có mật khẩu mặc định |

Windows CMD, tại thư mục gốc dự án: `mvn -DskipTests package && java -jar target\htac-backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=local`. Dừng phiên ứng dụng cũ bằng `Ctrl+C` trước khi chạy lại, vì Windows khóa JAR đang được sử dụng. Lệnh này tự biên dịch, đóng gói rồi khởi động; lưu code và chạy lại cùng một lệnh để thấy thay đổi. Trong đường dẫn hiện tại có ký tự Unicode, `spring-boot:run` đã tạo classpath khiến JVM không tìm thấy main class; dùng lệnh trên trong môi trường này.

Linux/macOS: `./mvnw -DskipTests package && java -jar target/htac-backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=local`. Khi chạy JAR, chọn `--spring.profiles.active=local` hoặc `deploy`; profile deploy yêu cầu HTTPS để cookie có cờ Secure. `GET http://localhost:8080/api/v1/public/ping` trả `{"status":"UP"}` nếu ứng dụng đã phục vụ HTTP. Các API nghiệp vụ chưa được mở ở N00.

Để chạy integration test, tạo database **riêng, có thể bỏ đi**, rồi đặt `HIS_TEST_DB_URL` (ví dụ JDBC URL tới database kiểm thử), `HIS_TEST_DB_USERNAME`, `HIS_TEST_DB_PASSWORD`. Test mặc định được bỏ qua nếu không có URL; khi có URL, Flyway tạo schema và test kiểm tra ràng buộc/giao dịch trên MySQL thật. Không trỏ biến kiểm thử tới database nghiệp vụ.

Schema được quản lý tại `src/main/resources/db/migration`. Không chỉnh `V1__initial_schema.sql` sau khi đã áp dụng ở bất kỳ môi trường dùng chung nào; tạo `V2__...sql` và thử trên bản sao có dữ liệu. `spring.jpa.hibernate.ddl-auto=validate` ngăn Hibernate tự sửa schema. Bản sao lưu và quy trình khôi phục thuộc N10.
