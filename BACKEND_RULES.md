# Quy ước backend HTAC

## Công nghệ và cấu trúc

- Java 25, Spring Boot 4.1.2, Maven Wrapper 3.3.4, MySQL 8.0.16+, Flyway, Spring Data JPA, Spring Security và Jakarta Validation.
- Base package `com.htac_backend`. Module: `modules.administration` (M01), `clinicdirectory` (M02), `scheduling` (M03), `reception` (M04), `outpatient` (M05), `pharmacy` (M06), `procurement` (M07), `reporting` (M08), `forecasting` (M09). Hạ tầng dùng `config`, `security`, `shared.api`, `shared.audit`.
- Tên Java dùng camelCase/PascalCase; bảng/cột, index, constraint dùng snake_case. Entity, repository, service, controller, DTO đặt trong module sở hữu. Enum lưu bằng tên chuỗi, không lưu ordinal.
- Mỗi module có package `dto`, `entity`, `repository`, `service`. API controller đặt ở `controller`, cấu hình riêng của module ở `config`, thành phần xác thực riêng của module ở `security` khi cần. Enum gắn trực tiếp với entity có thể đặt trong `entity`. Hạ tầng dùng chung tiếp tục ở `com.htac_backend.security`, `config` và `shared`; module đọc/tổng hợp M08–M09 không tạo repository ghi khi chưa sở hữu bảng.

## Quyền sở hữu dữ liệu

| Module | Bảng sở hữu |
| --- | --- |
| M01 | `user_account`, `hospital_config`, `account_password_reset_audit` |
| M02 | `specialty`, `clinic_room`, `doctor_profile` |
| M03 | `clinic_slot`, `appointment` |
| M04 | `patient` |
| M05 | `medical_visit`, `prescription`, `prescription_item` |
| M06 | `medicine`, `medicine_batch`, `supplier`, `stock_document`, `stock_document_item` |
| M07 | `purchase_request`, `purchase_request_item` |
| M08, M09 | Đọc/tổng hợp dữ liệu, không sở hữu thêm bảng trong bản 18 bảng |

M04 mở lượt qua service M05; M01 liên kết patient qua M04; M06 cập nhật phần chưa nhận qua M05; M07 dùng nghiệp vụ nhận kho M06. Tránh service gọi vòng. Một transaction bao trùm toàn bộ luồng ghi liên module. Với thao tác có cạnh tranh, khóa bản ghi điều phối trong MySQL và kiểm tra lại sau khóa; ràng buộc UNIQUE là lớp bảo vệ cuối.

## Dữ liệu và API

- Dùng `Instant` cho thời điểm, `LocalDate` cho ngày nghiệp vụ, `BigDecimal` cho số lượng/giá. Quy tắc MySQL và timezone ở `docs/SCHEMA_DECISIONS.md`.
- Audit dùng `AuditedEntity`; `created_by`/`updated_by` nhận ID từ principal M01 qua `AuthenticatedActor`. Khi bootstrap hoặc tác vụ hệ thống, để NULL và ghi rõ nguồn tác vụ.
- Không dùng `ddl-auto=create/update`; Flyway quản lý schema, Hibernate chỉ `validate`.
- DTO đầu vào có validation; service kiểm tra quyền trên đối tượng và trạng thái; REST dùng `docs/API_CONVENTIONS.md`.
- Không sửa dòng phiếu đã POSTED hay dữ liệu đã làm căn cứ cấp thuốc; xử lý sai sót bằng nghiệp vụ mới có truy vết.

## Cấu hình và kiểm thử

- Đọc `DB_URL` hoặc `DB_HOST`, `DB_PORT`, `DB_NAME`, cùng `DB_USERNAME`, `DB_PASSWORD` từ môi trường. Không commit bí mật.
- Unit/API test chạy không cần MySQL; integration test dùng database riêng qua `HIS_TEST_DB_URL`, `HIS_TEST_DB_USERNAME`, `HIS_TEST_DB_PASSWORD`.
- Migration đã áp dụng là bất biến. Thay đổi tiếp theo tạo `V2__...sql` và thử trên bản sao dữ liệu trước khi triển khai.
