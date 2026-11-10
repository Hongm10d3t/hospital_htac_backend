# Plan phát triển backend HTAC

Ngày lập 07/11/2026.

## 1. Căn cứ và cách sử dụng

Plan được xây dựng từ `PHAM_VI_DU_AN.md`, `DATABASE.md` và các quyết định công nghệ trong cuộc trao đổi. Phạm vi gồm một cơ sở khám bệnh, 18 bảng nghiệp vụ và năm vai trò ADMIN, RECEPTIONIST, DOCTOR, PHARMACIST, PATIENT.

Backend sử dụng Java OpenJDK 25 LTS, Spring Boot 4.1.x, Spring Web MVC, Spring Data JPA, Hibernate, MySQL, Spring Security, Jakarta Bean Validation, Maven Wrapper, Git/GitHub, Docker/Compose và Flyway. Khi khởi tạo, kiểm tra khả năng sử dụng và tương thích của bản vá cụ thể trước khi khóa phiên bản. Các thư viện hoặc dịch vụ ngoài tập đã chốt cần có quyết định riêng.

Plan giữ chín module nghiệp vụ M01–M09. N00 là nhóm công việc nền tảng dùng chung, N10 là nhóm tích hợp, đóng gói và bàn giao. Hai nhóm N00, N10 phục vụ triển khai kỹ thuật. Tên package bên dưới là đề xuất tổ chức code để dùng khi khởi tạo.

Mỗi gói có mã riêng, nội dung, mục tiêu và kết quả nghiệm thu dạng checklist. Có thể giao từng gói cho Agent. Trước khi bắt đầu, ghi người/Agent phụ trách và các gói tiên quyết vào issue hoặc phần ghi nhận của gói. Sau khi hoàn thành, ghi commit, cách chạy kiểm tra, kết quả thực tế và điểm còn tồn tại. Chỉ đánh dấu đạt khi có bằng chứng tương ứng.

Các mục nghiệm thu là tiêu chí cho công việc sắp thực hiện. Tài liệu này chưa xác nhận code hoặc kiểm thử đã hoàn thành.

### Quy ước nghiệm thu chung

- Gói có code phải build được qua Maven Wrapper và vượt qua các kiểm thử liên quan. Dùng MySQL để xác minh những quy tắc phụ thuộc SQL, khóa giao dịch và ràng buộc dữ liệu.
- API có hợp đồng đầu vào/đầu ra, quyền truy cập, lỗi nghiệp vụ và ví dụ gọi. Dùng tài liệu Markdown để ghi hợp đồng.
- Mỗi luồng ghi dữ liệu có kiểm tra dữ liệu hợp lệ, lỗi giữa giao dịch, quyền thao tác và nguy cơ ghi trùng khi gửi lại yêu cầu. Các luồng giữ chỗ, tiếp nhận, ghi sổ và nhận hàng phải kiểm tra cả yêu cầu đồng thời.
- Nghiệm thu quyền gồm đúng vai trò và đúng đối tượng dữ liệu. Ví dụ PATIENT chỉ đọc dữ liệu của mình, DOCTOR thao tác lượt được phân công.
- Dữ liệu mới và dữ liệu demo hợp lệ đều phải đi qua các quy tắc nghiệp vụ tương ứng. Trạng thái được kiểm tra tại backend và lưu đúng giá trị chuỗi đã thống nhất.
- Migration đã áp dụng được giữ nguyên nội dung. Thay đổi schema tiếp theo được ghi bằng migration mới, có kiểm tra trên dữ liệu đang tồn tại.

## 2. Danh sách module và thứ tự triển khai

| Mã  | Module hoặc nhóm công việc         | Package đề xuất                                  | Điều kiện bắt đầu chính                              |
| --- | ---------------------------------- | ------------------------------------------------ | ---------------------------------------------------- |
| N00 | Nền tảng backend                   | Cấu hình dự án, `config`, `security` và tài liệu | Hai tài liệu nguồn đã được đọc và đối chiếu          |
| M01 | Quản trị tài khoản và cấu hình     | `modules.administration`                         | N00, phối hợp M04.01–M04.02 cho đăng ký              |
| M02 | Danh mục khám bệnh và hồ sơ bác sĩ | `modules.clinicdirectory`                        | N00 và phần tài khoản nhân viên M01                  |
| M03 | Lịch khám và đặt hẹn               | `modules.scheduling`                             | M01, M02, hồ sơ bệnh nhân M04.01–M04.02              |
| M04 | Hồ sơ bệnh nhân và tiếp đón        | `modules.reception`                              | Hồ sơ làm sớm cùng M01, tiếp đón làm sau M03         |
| M05 | Khám ngoại trú và kê đơn           | `modules.outpatient`                             | Tiếp đón M04, danh mục thuốc M06.01                  |
| M06 | Quản lý dược và kho thuốc          | `modules.pharmacy`                               | Danh mục làm sớm, cấp phát cần đơn M05               |
| M07 | Đề xuất nhập thuốc                 | `modules.procurement`                            | M01 và M06.01–M06.05                                 |
| M08 | Báo cáo và thống kê                | `modules.reporting`                              | Dữ liệu nghiệp vụ M03–M07                            |
| M09 | Dữ liệu demo và học máy            | `modules.forecasting`, script dữ liệu riêng      | Luồng nghiệp vụ và định nghĩa tổng hợp đã ổn định    |
| N10 | Tích hợp, đóng gói và bàn giao     | Cấu hình chạy, kiểm thử, tài liệu vận hành       | Các module được chọn cho bản phát hành đã nghiệm thu |

Thứ tự thực hiện đề xuất theo các mốc dưới đây. Có thể triển khai một phần module trước để đáp ứng phụ thuộc của module khác.

1. Hoàn thành N00 và khung tài khoản M01.01–M01.02.
2. Làm hồ sơ M04.01–M04.02, sau đó nghiệm thu đăng ký M01.03 và hoàn thiện M01, M02.
3. Hoàn thành M03, phần tiếp đón còn lại của M04 và danh mục M06.01–M06.02.
4. Hoàn thành M05, sổ kho và cấp phát M06, đề xuất nhập M07.
5. Hoàn thành M08 và phần dữ liệu, hợp đồng tích hợp M09. Việc cung cấp mô hình đã đánh giá là một phụ thuộc riêng của demo dự báo.
6. Nghiệm thu N10. Bản nghiệp vụ có thể phát hành trước khi demo ML hoàn tất nếu phạm vi bản phát hành được ghi rõ.

### Quyền sở hữu dữ liệu giữa các module

| Dữ liệu                                                                           | Đơn vị quản lý chính | Cách phối hợp                                                                                      |
| --------------------------------------------------------------------------------- | -------------------- | -------------------------------------------------------------------------------------------------- |
| `user_account`, `hospital_config`, `account_password_reset_audit`                 | M01                  | Module khác dùng định danh và quyền đã xác thực; audit đặt lại mật khẩu thuộc M01                   |
| `patient`                                                                         | M04                  | M01 gọi nghiệp vụ liên kết/tạo hồ sơ trong cùng giao dịch đăng ký                                  |
| `specialty`, `clinic_room`, `doctor_profile`                                      | M02                  | M03 sử dụng để kiểm tra phân công                                                                  |
| `clinic_slot`, `appointment`                                                      | M03                  | M04 dùng cơ chế kiểm tra sức chứa chung và xử lý lịch hẹn khi tiếp nhận                            |
| `medical_visit`, `prescription`, `prescription_item`                              | M05                  | M04 gọi thao tác mở/kết thúc lượt thuộc phạm vi tiếp đón, M06 gọi thao tác cập nhật phần chưa nhận |
| `medicine`, `medicine_batch`, `supplier`, `stock_document`, `stock_document_item` | M06                  | M05 đọc danh mục, M07 phối hợp ghi nhận hàng qua nghiệp vụ kho                                     |
| `purchase_request`, `purchase_request_item`                                       | M07                  | M06 kiểm tra điều kiện nhận hàng theo đề xuất trước khi ghi sổ                                     |
| Dữ liệu báo cáo, dataset, kết quả ML                                              | M08, M09 khai thác   | Dùng truy vấn phù hợp và hợp đồng dữ liệu thống nhất                                               |

Đây là đề xuất quyền sở hữu code. Chốt ở N00.01 để mỗi bảng có một entity và repository quản lý ghi. Các service liên module phải có hướng phụ thuộc rõ, tránh việc hai service gọi vòng nhau. Có thể đặt service điều phối ở luồng sử dụng để gom giao dịch nhiều module.

## 3. N00 — Nền tảng backend

### N00.01 — Khởi tạo dự án và quy tắc cho Agent

**Nội dung gói công việc**

- Khởi tạo Spring Boot, Maven Wrapper và cấu trúc package theo module. Chọn bản vá tương thích của các công nghệ đã chốt.
- Lập `AGENTS.md` và `BACKEND_RULES.md` từ phạm vi, database và plan này. Quy định cách đọc tài liệu, nhận gói, giới hạn thay đổi, chạy kiểm thử và báo cáo kết quả.
- Ghi quyền sở hữu entity, giao dịch liên module, quy ước đặt tên, enum, DTO và vị trí migration. Thêm rule để Agent báo rõ mâu thuẫn nghiệp vụ trước khi triển khai phần phụ thuộc.

**Mục tiêu gói công việc**

Tạo nền tảng thống nhất để người dùng và Agent theo dõi code theo từng gói, hạn chế triển khai trùng hoặc tự mở rộng phạm vi.

**Kết quả nghiệm thu**

- [x] Checkout mới chạy được lệnh build qua Maven Wrapper theo hướng dẫn.
- [x] Có hai file rule và bảng ánh xạ M01–M09 với package, dữ liệu sở hữu.
- [x] Agent có thể nhận một mã gói, xác định tài liệu đầu vào, file dự kiến sửa và tiêu chí hoàn tất.

### N00.02 — Đối chiếu thiết kế và chốt quy tắc triển khai

**Nội dung gói công việc**

- Lập danh sách đủ 18 bảng, cột, ràng buộc, trường audit, enum và các điều kiện nghiệp vụ theo hai tài liệu nguồn.
- Đối chiếu FK của `clinic_slot.doctor_id`, `prescription.doctor_id` giữa phần trường và bảng quan hệ. Đề xuất tham chiếu `doctor_profile.user_id` theo mô tả trường, ghi rõ kết luận trước khi viết migration. Làm rõ đích của `doctor_profile.specialty_id` là `specialty.id`.
- Chốt ánh xạ thời gian sang MySQL, độ chính xác số lượng/giá, timezone bệnh viện dùng để xác định ngày, quy tắc NULL và các chỉ mục cần thiết.
- Ghi các điểm cần quyết định trước gói liên quan, gồm đổi role tài khoản đã có dữ liệu, sửa/hủy phiếu đã POSTED, ý nghĩa cấp lại sau trả thuốc, giới hạn truy cập lịch sử bệnh nhân và cách xác minh liên kết hồ sơ.

**Mục tiêu gói công việc**

Có đặc tả triển khai nhất quán với MySQL và bảo toàn ý nghĩa nghiệp vụ đã chốt.

**Kết quả nghiệm thu**

- [x] Có bảng ánh xạ kiểu dữ liệu nguồn sang MySQL, trong đó mọi trường `timestamptz` có quy ước lưu và chuyển đổi rõ ràng.
- [x] Mâu thuẫn FK được ghi kèm quyết định được xác nhận, còn quy tắc chưa rõ được gắn gói chịu ảnh hưởng và điều kiện phải chốt.
- [x] Quy ước ngày nghiệp vụ có ví dụ qua ranh giới ngày để dùng chung cho lịch, số thứ tự, báo cáo và dataset.

### N00.03 — Schema MySQL và Flyway

**Nội dung gói công việc**

- Viết migration tạo đủ 18 bảng, PK, FK, unique, giá trị mặc định, ràng buộc và chỉ mục theo N00.02.
- Bố trí audit tự tham chiếu tài khoản và dữ liệu khởi tạo tối thiểu. Tách dữ liệu demo lớn khỏi migration cấu trúc.
- Cấu hình Flyway quản lý schema và Hibernate kiểm tra mapping. Thiết lập quy tắc tạo migration mới khi thay đổi schema.

**Mục tiêu gói công việc**

Khởi tạo và nâng cấp database có thể lặp lại, có lịch sử phiên bản.

**Kết quả nghiệm thu**

- [x] Database MySQL trống được tạo đầy đủ bằng Flyway, khởi động lần tiếp theo thành công với cùng schema.
- [x] Các trường hợp trùng username, mã nghiệp vụ, một tài khoản gắn nhiều patient, một lịch hẹn gắn nhiều lượt bị chặn đúng ràng buộc.
- [ ] Có minh chứng chạy migration tiếp theo trên bản sao có dữ liệu mà các bản ghi hợp lệ vẫn đọc và liên kết được.

### N00.04 — Hợp đồng REST API và xử lý lỗi

**Nội dung gói công việc**

- Thống nhất đường dẫn, DTO yêu cầu/phản hồi, phân trang, lọc, sắp xếp, định dạng ngày giờ và số thập phân.
- Xây dựng validation và xử lý lỗi tập trung cho dữ liệu thiếu, truy cập trái quyền, đối tượng không tồn tại và xung đột nghiệp vụ.
- Tạo mẫu tài liệu API để từng gói ghi endpoint, role, điều kiện dữ liệu, ví dụ và lỗi.

**Mục tiêu gói công việc**

Frontend và Agent có cùng quy ước khi gọi hoặc bổ sung API.

**Kết quả nghiệm thu**

- [x] Có tài liệu quy ước API và một luồng mẫu thể hiện thành công, lỗi validation, lỗi quyền và lỗi nghiệp vụ.
- [x] Phản hồi không chứa entity tuần tự hóa ngoài kiểm soát, mật khẩu băm hoặc stack trace nội bộ.
- [x] Bộ lọc, trang vượt phạm vi và đầu vào sắp xếp không hợp lệ được xử lý xác định.

### N00.05 — Cấu hình môi trường, giao dịch và audit

**Nội dung gói công việc**

- Tách cấu hình local, kiểm thử và triển khai qua profile/biến môi trường. Cung cấp file cấu hình mẫu cho MySQL và ứng dụng.
- Quy định ranh giới transaction, rollback khi lỗi giữa chừng và cách đọc người thao tác cho audit.
- Cấu hình logging phục vụ truy vết thao tác, giới hạn dữ liệu nhạy cảm được ghi log. Chuẩn bị kiểm thử chạy với MySQL riêng.

**Mục tiêu gói công việc**

Các module có nền tảng chạy và ghi dữ liệu nhất quán giữa các môi trường.

**Kết quả nghiệm thu**

- [x] Chạy ứng dụng bằng cấu hình mẫu và biến môi trường, thông tin bí mật được cung cấp bên ngoài mã nguồn.
- [x] Một giao dịch mẫu lỗi giữa chừng rollback toàn bộ thay đổi liên quan.
- [x] Audit phản ánh đúng người và thời điểm thao tác, kiểm thử sử dụng database riêng có hướng dẫn tạo lại.

## 4. M01 — Quản trị tài khoản và cấu hình

### M01.01 — Đăng nhập, đăng xuất và kiểm soát truy cập

**Nội dung gói công việc**

- Xây dựng đăng nhập bằng tài khoản hoạt động, kiểm tra mật khẩu băm, đăng xuất và lấy thông tin người đang đăng nhập.
- Cấu hình Spring Security theo năm role, quyền xem công khai, session và các yêu cầu bảo vệ luồng trình duyệt theo cách frontend kết nối.
- Áp dụng kiểm tra quyền đối tượng tại các service nghiệp vụ và thống nhất cách xử lý tài khoản bị khóa khi còn phiên đăng nhập.

**Mục tiêu gói công việc**

Mọi API sử dụng cùng cơ chế xác thực và kiểm tra quyền.

**Kết quả nghiệm thu**

- [x] Đăng nhập đúng/sai, tài khoản khóa, phiên hết hạn và đăng xuất đều có phản hồi đúng hợp đồng.
- [x] Người chưa đăng nhập chỉ sử dụng API công khai, từng role bị chặn ở API ngoài quyền.
- [x] Có kiểm thử riêng cho phiên đang tồn tại khi tài khoản bị khóa theo quy tắc đã chốt.

Ghi nhận M01.01: cấu hình từ chối mặc định mọi route chưa cấp quyền. M01.02 bổ sung API nhân viên chỉ ADMIN được phép; kiểm thử API đã xác nhận người chưa đăng nhập và bốn role khác bị chặn.

### M01.02 — Quản lý tài khoản nhân viên

**Nội dung gói công việc**

- ADMIN tạo, tra cứu, cập nhật, khóa và mở tài khoản RECEPTIONIST, DOCTOR, PHARMACIST.
- Kiểm tra username, số điện thoại bắt buộc và quy tắc role. Khi tạo bác sĩ, phối hợp M02 để tạo hồ sơ chuyên môn trong cùng giao dịch.
- Áp dụng quy tắc thay đổi role của tài khoản đã liên kết dữ liệu theo N00.02.

**Mục tiêu gói công việc**

Cung cấp tài khoản đúng trách nhiệm, duy trì liên kết lịch sử khi thay đổi trạng thái sử dụng.

**Kết quả nghiệm thu**

- [x] Chỉ ADMIN thực hiện được thao tác quản trị nhân viên, dữ liệu tài khoản không hợp lệ bị từ chối.
- [x] Tạo bác sĩ thất bại ở bước hồ sơ thì tài khoản cũng được rollback.
- [x] Khóa tài khoản ngăn sử dụng hệ thống theo quy tắc phiên đã chốt và lịch sử nghiệp vụ vẫn tra cứu được bởi người có quyền.

Ghi nhận M01.02: `StaffAccountApiTest` kiểm tra 401/403/validation; `StaffAccountDatabaseIntegrationTest` chạy trên MySQL riêng, kiểm tra rollback khi chuyên khoa ngừng hoạt động, tạo hồ sơ bác sĩ, khóa/mở giữ hồ sơ và ADMIN vẫn tra cứu được tài khoản. Kiểm thử M01.01 xác nhận session bị hủy ở yêu cầu tiếp theo khi `is_active=false`. API tra cứu lịch sử lâm sàng thuộc các gói nghiệp vụ sau.

### M01.03 — Đăng ký tài khoản bệnh nhân

**Nội dung gói công việc**

- Nhận thông tin đăng ký, tạo tài khoản PATIENT và gọi nghiệp vụ hồ sơ M04.01–M04.02.
- Xử lý CCCD chưa có hồ sơ, hồ sơ chưa liên kết, hồ sơ đã liên kết và thông tin cần tiếp đón đối chiếu.
- Thực hiện tạo tài khoản và liên kết/tạo patient trong một transaction, xử lý hai yêu cầu đăng ký trùng đồng thời.

**Mục tiêu gói công việc**

Bệnh nhân có tài khoản và truy cập đúng hồ sơ, bao gồm lịch sử đã phát sinh trước đăng ký.

**Kết quả nghiệm thu**

- [x] Đăng ký luôn nhận role PATIENT và yêu cầu số điện thoại, dữ liệu role gửi thêm không làm tăng quyền.
- [x] Hồ sơ cũ hợp lệ được liên kết theo quy trình xác minh đã chốt, không tạo bản sao của hồ sơ đã xác định.
- [x] Yêu cầu đồng thời hoặc lỗi liên kết không tạo tài khoản/hồ sơ dở dang hay gắn hai tài khoản vào cùng patient.

Ghi nhận M01.03: đăng ký công khai trả 409 và không tạo tài khoản khi hồ sơ cần đối chiếu; RECEPTIONIST cấp mã một lần sau kiểm tra trực tiếp. `PatientRegistrationApiTest` kiểm tra CSRF, role; `PatientRegistrationDatabaseIntegrationTest` chạy trên MySQL riêng kiểm tra hồ sơ mới/cũ, mã hết hạn, rollback và hai yêu cầu cùng CCCD. Migration V2 lưu hash/hạn mã trên `patient`; API lịch sử cá nhân được mở ở M04.05.

### M01.04 — Thông tin cá nhân và mật khẩu

**Nội dung gói công việc**

- Cho người dùng cập nhật tên hiển thị, số điện thoại và đổi mật khẩu theo điều kiện xác thực.
- Cho ADMIN hỗ trợ đặt lại mật khẩu trong demo sau bước đối chiếu thông tin.
- Phân biệt dữ liệu tài khoản với hồ sơ định danh bệnh nhân, ghi audit cho thay đổi.

**Mục tiêu gói công việc**

Người dùng duy trì thông tin truy cập và liên hệ đúng phạm vi quyền.

**Kết quả nghiệm thu**

- [x] Người dùng chỉ cập nhật tài khoản của mình qua API cá nhân, mật khẩu cũ/sai điều kiện được kiểm tra đúng.
- [x] ADMIN đặt lại mật khẩu có audit, API và log không trả hoặc ghi mật khẩu gốc.
- [x] Thay đổi tên hiển thị không vô tình ghi đè họ tên định danh của patient.

Ghi nhận M01.04: `PersonalAccountApiTest` kiểm tra quyền và CSRF; `PersonalAccountDatabaseIntegrationTest` trên MySQL riêng kiểm tra mật khẩu cũ, điều kiện mật khẩu mới, audit người/phương thức đặt lại và tên định danh patient không đổi. `AuthenticationApiTest` xác nhận session cũ bị hủy sau khi `credential_version` tăng. Migration V3 thêm phiên bản thông tin xác thực và bảng audit bảo mật.

### M01.05 — Thông tin và cấu hình bệnh viện

**Nội dung gói công việc**

- Quản lý một bản ghi thông tin cơ sở, tên, địa chỉ, hotline, email, logo URL và giới thiệu.
- Quản lý `advance_booking_days` với giá trị lớn hơn 0, cung cấp thông tin công khai phù hợp.
- Cho M03 đọc cấu hình thời hạn đặt trước trong thao tác đặt lịch.

**Mục tiêu gói công việc**

Thông tin cơ sở và chính sách đặt trước có một nguồn cấu hình thống nhất.

**Kết quả nghiệm thu**

- [x] Chỉ ADMIN cập nhật cấu hình, thao tác đồng thời không tạo nhiều bản cấu hình cho một cơ sở.
- [x] Thay đổi thời hạn đặt trước ảnh hưởng đúng đến yêu cầu đặt mới, đầu vào không hợp lệ bị từ chối.

Ghi nhận M01.05: `HospitalConfigApiTest` kiểm tra API công khai, quyền ADMIN và validation; `HospitalConfigDatabaseIntegrationTest` trên MySQL riêng kiểm tra tạo/cập nhật một bản ghi, hai ghi đầu đồng thời và chính sách đặt trước đổi từ 7 sang 14 ngày. `HospitalConfigService` công bố API đọc và kiểm tra khoảng ngày cho M03. `AppointmentBookingDatabaseIntegrationTest` kiểm tra cùng một slot ngoài thời hạn bị từ chối, sau khi ADMIN tăng thời hạn thì yêu cầu đặt thật được chấp nhận.

## 5. M02 — Danh mục khám bệnh và hồ sơ bác sĩ

### M02.01 — Danh mục chuyên khoa và phòng khám

**Nội dung gói công việc**

- Tạo, sửa, tra cứu và thay đổi trạng thái chuyên khoa, phòng khám.
- Kiểm tra mã duy nhất và mỗi phòng thuộc một chuyên khoa.
- Khóa thay đổi chuyên khoa của phòng đã phát sinh lịch sử, sử dụng phòng mới khi tổ chức lại.

**Mục tiêu gói công việc**

Có dữ liệu phân công khám và phân nhóm báo cáo ổn định.

**Kết quả nghiệm thu**

- [x] Mã trùng, chuyên khoa không tồn tại và chuyển chuyên khoa trái điều kiện bị từ chối.
- [x] Dữ liệu ngừng hoạt động vẫn tham chiếu được trong lịch sử và bị kiểm tra khi lập lịch mới.

Ghi nhận M02.01: `ClinicDirectoryAdminApiTest` kiểm tra quyền ADMIN và validation; `ClinicDirectoryDatabaseIntegrationTest` chạy trên MySQL riêng xác nhận mã trùng, chuyên khoa thiếu/ngừng hoạt động, đổi chuyên khoa phòng trước khi có slot và từ chối sau khi có slot. Phòng/chuyên khoa ngừng hoạt động vẫn đọc được; `ClinicDirectoryLookupService` từ chối chúng cho phân công mới. M03.01 đã kiểm thử qua service lập lịch thật: phòng ngừng hoạt động bị từ chối, slot cũ vẫn tồn tại.

### M02.02 — Hồ sơ chuyên môn bác sĩ

**Nội dung gói công việc**

- Tạo hồ sơ với PK đồng thời là FK `user_id`, kiểm tra tài khoản DOCTOR và chuyên khoa.
- Quản lý ảnh URL, chức danh, chức vụ, năm bắt đầu hành nghề, giới thiệu, chuyên sâu và chứng chỉ.
- Kết hợp M01.02 trong luồng tạo bác sĩ, kiểm soát cập nhật thông tin đã liên quan lịch khám.

**Mục tiêu gói công việc**

Mỗi bác sĩ có hồ sơ chuyên môn dùng được cho phân lịch và hiển thị.

**Kết quả nghiệm thu**

- [x] Mỗi tài khoản bác sĩ có tối đa một hồ sơ, tài khoản khác role bị từ chối liên kết.
- [x] Năm bắt đầu hành nghề và dữ liệu chuyên khoa được validation, chức vụ hiển thị không làm thay đổi quyền hệ thống.

Ghi nhận M02.02: `DoctorProfileDatabaseIntegrationTest` trên MySQL riêng xác nhận PK/FK, role DOCTOR, chuyên khoa hoạt động, năm hành nghề, chức vụ không đổi role và khóa đổi chuyên khoa sau khi có lịch; `DoctorProfileAdminApiTest` xác nhận quyền ADMIN, validation và DTO. `mvnw.cmd test`: 55 kiểm thử thành công.

### M02.03 — Công khai và tra cứu bác sĩ

**Nội dung gói công việc**

- ADMIN bật/tắt công khai hồ sơ, xây dựng API danh sách và chi tiết công khai, lọc theo chuyên khoa.
- Thiết kế DTO chỉ gồm thông tin được công bố và liên kết tới lịch đang nhận đăng ký.

**Mục tiêu gói công việc**

Bệnh nhân tìm được bác sĩ phù hợp để chuyển sang luồng đặt khám.

**Kết quả nghiệm thu**

- [x] Hồ sơ chưa công khai không xuất hiện trong danh sách hoặc truy cập chi tiết công khai.
- [x] Phản hồi không chứa mật khẩu, thông tin đăng nhập hoặc số điện thoại cá nhân, bộ lọc trả đúng hồ sơ.

Ghi nhận M02.03: `DoctorPublicDatabaseIntegrationTest` trên MySQL riêng xác nhận bật/tắt công khai, lọc chuyên khoa, ẩn tài khoản/chuyên khoa không hoạt động; `DoctorPublicApiTest` xác nhận khách chưa đăng nhập truy cập được và DTO không có thông tin đăng nhập/số điện thoại. M03.03 đã triển khai và kiểm thử endpoint `availableSlotsUrl` trên lịch còn nhận đăng ký.

## 6. M03 — Lịch khám và đặt hẹn

### M03.01 — Tạo lịch khám và kiểm tra phân công

**Nội dung gói công việc**

- ADMIN tạo khung đơn hoặc nhiều khung theo tuần/ngày, khoảng thời gian, thời lượng và sức chứa.
- Kiểm tra thời gian bắt đầu/kết thúc, capacity, bác sĩ và phòng hoạt động, chuyên khoa phù hợp.
- Ngăn giao nhau với lịch của cùng phòng hoặc cùng bác sĩ, kể cả khi hai yêu cầu tạo lịch chạy đồng thời.

**Mục tiêu gói công việc**

Có lịch khám khả thi để tiếp nhận lịch hẹn và bệnh nhân trực tiếp.

**Kết quả nghiệm thu**

- [x] Tạo theo buổi sinh đúng số khung và khoảng thời gian, khung liền kề được xử lý theo quy ước đã ghi.
- [x] Lịch chồng phòng/bác sĩ, sai chuyên khoa hoặc dữ liệu không hoạt động bị chặn.
- [x] Tạo đồng thời không sinh lịch trùng, lỗi một phần của lô tạo được xử lý theo hợp đồng transaction đã chọn.

Ghi nhận M03.01: `ClinicSlotAdminApiTest` kiểm tra quyền ADMIN, CSRF và validation; `ClinicSlotDatabaseIntegrationTest` trên MySQL riêng kiểm tra lô hai ngày, khung liền kề, giao giờ theo bác sĩ/phòng, sai chuyên khoa, phòng ngừng hoạt động, lô có lỗi không ghi dở và hai yêu cầu đồng thời chỉ tạo một slot. `mvnw.cmd test`: 63 kiểm thử thành công.

### M03.02 — Điều chỉnh, đóng khung và sức chứa dùng chung

**Nội dung gói công việc**

- Áp dụng điều kiện sửa khung, bảo vệ thông tin khung đã có lượt thực tế và xử lý khung có lịch hẹn.
- Đóng OPEN sang CLOSED để ngừng nhận thêm, duy trì khả năng xử lý lịch đã tồn tại.
- Viết một quy tắc tính sức chứa dùng chung cho đặt hẹn và tiếp đón. Tính suất hẹn đang giữ, lượt đã tiếp nhận và lượt trực tiếp, loại trùng phần hẹn đã chuyển thành lượt.

**Mục tiêu gói công việc**

Các luồng vào hệ thống sử dụng cùng một giới hạn tiếp nhận.

**Kết quả nghiệm thu**

- [x] Đóng khung không xóa lịch hẹn, sửa khung có lượt thực tế bị từ chối.
- [x] Ví dụ capacity 2, một hẹn đang giữ suất và một lượt trực tiếp thì hệ thống báo hết chỗ. Tiếp nhận người có hẹn không tính họ thêm lần nữa.
- [x] Quy tắc xử lý sức chứa khi hủy hẹn, lượt NOT_COMPLETED hoặc VOIDED được ghi rõ và có ca kiểm tra tương ứng.

Ghi nhận M03.02: `SlotCapacityDatabaseIntegrationTest` trên MySQL riêng kiểm tra khung đóng giữ hẹn, sửa khung có hẹn/lượt, công thức sức chứa với hẹn đang giữ, lượt trực tiếp, hẹn chuyển thành lượt, NOT_COMPLETED và VOIDED. `ClinicSlotAdminApiTest` xác nhận quyền cập nhật/đóng khung và CSRF. `mvnw.cmd test`: 66 kiểm thử thành công. `SlotCapacityService` là hợp đồng chung cho M03.03 và M04/M05; kiểm thử xuyên module tiếp đón thật phụ thuộc M04.

### M03.03 — Tra cứu lịch và đặt khám cho bản thân

**Nội dung gói công việc**

- Cho PATIENT tìm khung theo ngày, chuyên khoa, bác sĩ trong thời hạn đặt trước.
- Tạo lịch PENDING cho patient liên kết tài khoản, ghi lý do khám, mã lịch và thời điểm tạo.
- Kiểm tra lại sức chứa trong transaction, kiểm soát yêu cầu đặt đồng thời hoặc gửi lặp.

**Mục tiêu gói công việc**

Bệnh nhân đặt được suất hợp lệ và theo dõi lịch cá nhân.

**Kết quả nghiệm thu**

- [x] PENDING và CONFIRMED đều giữ suất, lịch quá thời hạn hoặc CLOSED bị từ chối đặt mới.
- [x] Sửa patient ID trong yêu cầu không cho phép đặt hộ hoặc đọc lịch người khác.
- [x] Hai người tranh suất cuối chỉ có số lịch thành công đúng capacity, gửi lại yêu cầu có hành vi rõ ràng theo hợp đồng.

Ghi nhận M03.03: `AppointmentBookingDatabaseIntegrationTest` trên MySQL riêng kiểm tra hồ sơ công khai, thời hạn đặt trước, PENDING/CONFIRMED giữ suất, khung đóng, gửi lặp và hai PATIENT tranh suất cuối; `AppointmentBookingApiTest` kiểm tra quyền PATIENT và không nhận patientId trong DTO. M03.04 đã bổ sung đọc lịch cá nhân và kiểm thử ID của bệnh nhân khác trả 404.

### M03.04 — Xác nhận, theo dõi và kết thúc lịch hẹn

**Nội dung gói công việc**

- RECEPTIONIST xem thông tin liên hệ từ tài khoản, ghi CONFIRMED sau khi xác nhận qua điện thoại.
- Lưu người và thời điểm xác nhận, cập nhật COMPLETED hoặc NOT_COMPLETED theo kết quả thực tế và lý do.
- Đồng bộ kết quả với luồng tiếp đón/khám, cung cấp danh sách lịch cá nhân cho PATIENT.

**Mục tiêu gói công việc**

Trạng thái lịch hẹn phản ánh quá trình xử lý và kết nối đúng với lượt khám.

**Kết quả nghiệm thu**

- [x] Xác nhận lưu đúng nhân viên/thời điểm, kết thúc NOT_COMPLETED yêu cầu lý do và thời điểm đóng.
- [x] Hẹn chưa được tiếp nhận không tạo medical_visit hoặc được tính vào lượt đến.
- [ ] Cập nhật cuối từ quy trình khám và thao tác tiếp đón không ghi đè trạng thái trái quy tắc khi chạy đồng thời.

Ghi nhận M03.04: `AppointmentLifecycleDatabaseIntegrationTest` trên MySQL riêng kiểm tra quyền lịch cá nhân, xác nhận, liên hệ từ tài khoản, hoàn tất theo lượt, NOT_COMPLETED và hai cập nhật kết quả cuối chạy đồng thời; `ReceptionAppointmentApiTest` kiểm tra role/CSRF. `mvnw.cmd test`: 75 kiểm thử thành công. Hợp đồng nội bộ `completeFromVisit` và `notCompleteFromVisit` đã có, nhưng tiêu chí cuối còn mở đến khi M05 có quy trình cập nhật lượt khám thật để kiểm thử xuyên module.

## 7. M04 — Hồ sơ bệnh nhân và tiếp đón

### M04.01 — Tạo, tra cứu và cập nhật hồ sơ bệnh nhân

**Nội dung gói công việc**

- Quản lý mã bệnh nhân, họ tên, ngày sinh, giới tính, địa chỉ, CCCD và liên kết tài khoản tùy chọn.
- Tìm theo mã, CCCD hoặc thông tin định danh, hỗ trợ tiếp đón chọn lại hồ sơ đã tồn tại.
- Cho tạo người đến trực tiếp chưa có tài khoản/CCCD, kiểm tra duy nhất khi có CCCD.

**Mục tiêu gói công việc**

Mỗi bệnh nhân được theo dõi xuyên suốt nhiều lần khám với hồ sơ định danh phù hợp.

**Kết quả nghiệm thu**

- [x] Tạo hồ sơ trực tiếp thành công với `user_id` và CCCD trống, không yêu cầu cột phone trong patient.
- [x] Mã bệnh nhân hoặc CCCD trùng bị chặn, cập nhật thông tin không làm mất liên kết lịch sử.
- [x] PATIENT chỉ xem được hồ sơ của mình, RECEPTIONIST thao tác trong phạm vi hồ sơ hành chính.

Ghi nhận M04.01: `PatientDirectoryApiTest` kiểm tra quyền, CSRF, validation và DTO không có phone; `PatientDirectoryDatabaseIntegrationTest` trên MySQL riêng xác nhận hồ sơ trực tiếp không có tài khoản/CCCD, mã và CCCD duy nhất, tra cứu theo mã/CCCD/định danh, cập nhật giữ liên kết tài khoản và vô hiệu mã xác minh cũ khi định danh đổi. `mvnw.cmd test`: 81 kiểm thử thành công. Không có migration mới.

### M04.02 — Liên kết hồ sơ cũ và tài khoản

**Nội dung gói công việc**

- Xây dựng nghiệp vụ dùng chung cho M01.03 để tìm hồ sơ bằng CCCD và liên kết nếu đủ điều kiện xác minh.
- Ghi quy trình tiếp đón đối chiếu hồ sơ thiếu/sai CCCD, bổ sung trên hồ sơ đã xác định.
- Xử lý hồ sơ đã liên kết và kiểm tra cạnh tranh khi nhiều yêu cầu cùng liên kết một patient.

**Mục tiêu gói công việc**

Giữ lịch sử khám cũ và gắn nó với đúng chủ tài khoản.

**Kết quả nghiệm thu**

- [x] Sau liên kết, bệnh nhân thấy các lượt trước khi đăng ký và các lượt mới trong cùng hồ sơ.
- [x] Thông tin chưa đủ xác minh hoặc hồ sơ đã gắn tài khoản được xử lý theo quy trình đã chốt.
- [x] Hai giao dịch đồng thời không chiếm cùng hồ sơ, lỗi liên kết rollback cùng thao tác tạo tài khoản.

Ghi nhận M04.02: Luồng đối chiếu dùng API M01.03 và cập nhật hồ sơ M04.01. `PatientRegistrationDatabaseIntegrationTest` kiểm tra hồ sơ thiếu/sai CCCD, cấp mã sau khi sửa đúng hồ sơ, hồ sơ đã liên kết, mã hết hạn, hai đăng ký mới cạnh tranh và hai đăng ký cùng mã xác minh cạnh tranh; giao dịch thua không để lại tài khoản. `PatientHistoryDatabaseIntegrationTest` của M04.05 xác nhận lượt tạo trước đăng ký và lượt tạo sau liên kết cùng xuất hiện cho một patient ID.

### M04.03 — Tiếp nhận người có hẹn và người đến trực tiếp

**Nội dung gói công việc**

- Tiếp nhận lịch hợp lệ khi bệnh nhân đến, mở lượt gắn đúng patient, appointment và slot.
- Tiếp nhận trực tiếp bằng khung thực tế có khả năng tiếp nhận, lưu `appointment_id` trống.
- Dùng quy tắc sức chứa M03.02, ghi `arrived_at`, mã lượt và trạng thái WAITING trong giao dịch.

**Mục tiêu gói công việc**

Ghi đầy đủ lượt bệnh nhân đã thực sự đến cho quy trình khám và dữ liệu dự báo.

**Kết quả nghiệm thu**

- [x] Một lịch hẹn có tối đa một lượt, gửi lại thao tác tiếp nhận không tạo lượt thứ hai.
- [x] Cả người có hẹn và trực tiếp đều xuất hiện trong danh sách chờ đúng phòng/bác sĩ.
- [x] Hai luồng tiếp nhận cạnh tranh sức chứa vẫn tuân theo giới hạn chung và không ghi dữ liệu dở dang.

Ghi nhận M04.03: `ReceptionDatabaseIntegrationTest` trên MySQL riêng kiểm tra tiếp nhận hẹn gửi lặp, trực tiếp, danh sách chờ, cạnh tranh hai lượt trực tiếp và hẹn với trực tiếp. `ReceptionVisitApiTest` kiểm tra role, CSRF, validation. M05 sở hữu entity/repository/service ghi `medical_visit`; M04 điều phối khóa slot, appointment, phòng và giao dịch READ_COMMITTED. Không có migration mới.

### M04.04 — Cấp số thứ tự và xử lý lượt tiếp đón

**Nội dung gói công việc**

- Cấp số dương, duy nhất trong cùng phòng và ngày nghiệp vụ, cung cấp danh sách chờ để tiếp đón theo dõi.
- Áp dụng cơ chế đồng bộ cấp số dùng được với nhiều yêu cầu backend đồng thời.
- Cho ghi NOT_COMPLETED khi bệnh nhân bỏ dở và VOIDED khi nhập nhầm trong phạm vi quyền, kiểm tra các dữ liệu liên quan trước thao tác.

**Mục tiêu gói công việc**

Danh sách chờ có thứ tự rõ ràng và dữ liệu lượt đến được phân loại đúng.

**Kết quả nghiệm thu**

- [x] Tiếp nhận đồng thời trong cùng phòng/ngày không sinh số trùng, các phòng hoặc ngày khác có phạm vi số riêng.
- [x] NOT_COMPLETED và VOIDED yêu cầu lý do, tiếp đón không sửa nội dung chuyên môn.
- [x] Lượt đã phát sinh đơn hoặc phiếu kho được xử lý theo điều kiện đã chốt, thống kê vẫn tính NOT_COMPLETED và loại VOIDED.

Ghi nhận M04.04: `ReceptionDatabaseIntegrationTest` trên MySQL riêng kiểm tra hai khung cùng phòng cấp số đồng thời, số VOIDED không tái dùng, NOT_COMPLETED giữ lượt thực tế, VOIDED loại khỏi sức chứa, đồng bộ lịch hẹn, chặn VOIDED khi đã có đơn thuốc. `ReceptionVisitApiTest` kiểm tra lý do, role và CSRF. Khóa theo phòng; bộ đếm MAX theo `(room_id, queue_date)` cho dãy số riêng từng ngày; ràng buộc UNIQUE trong V1 là lớp chặn cuối. Không có migration mới.

### M04.05 — Tra cứu lịch sử cá nhân

**Nội dung gói công việc**

- Xây dựng API danh sách lượt, kết quả và đơn thuốc của patient đang đăng nhập.
- Kết hợp dữ liệu M05 với hồ sơ M04, hỗ trợ phân trang và lọc thời gian.

**Mục tiêu gói công việc**

Bệnh nhân tra cứu được toàn bộ lịch sử thuộc hồ sơ đã liên kết.

**Kết quả nghiệm thu**

- [x] Kết quả gồm dữ liệu trước và sau khi có tài khoản, theo chính sách hiển thị trạng thái đã chốt.
- [x] Thay ID lượt, đơn hoặc patient trong URL/yêu cầu không đọc được dữ liệu người khác.

Ghi nhận M04.05: `PatientHistoryDatabaseIntegrationTest` trên MySQL riêng tạo lượt cũ trước liên kết, cấp mã xác minh/đăng ký, tạo lượt mới, xác nhận phân trang/lọc ngày, kết quả, đơn ISSUED, chặn đơn DRAFT và ID của patient khác. `PatientHistoryApiTest` kiểm tra PATIENT-only và `Cache-Control: no-store`. M05 cung cấp hợp đồng đọc lượt/đơn, M06 cung cấp tên và đơn vị thuốc. Không có migration mới.

## 8. M05 — Khám ngoại trú và kê đơn

### M05.01 — Danh sách công việc và bắt đầu khám

**Nội dung gói công việc**

- Cho bác sĩ xem lịch được phân công, bệnh nhân chờ theo phòng/khung và lịch sử cần thiết phục vụ lượt khám.
- Chuyển WAITING sang IN_PROGRESS, ghi thời điểm bắt đầu và kiểm tra bác sĩ phụ trách.
- Kiểm soát yêu cầu bắt đầu lặp hoặc đồng thời.

**Mục tiêu gói công việc**

Bác sĩ tiếp nhận đúng công việc và ghi nhận quá trình khám nhất quán.

**Kết quả nghiệm thu**

- [x] Bác sĩ thao tác được lượt được phân công, bác sĩ khác bị từ chối ghi.
- [x] Bắt đầu hai lần không ghi đè sai thời điểm ban đầu hoặc tạo lượt mới.
- [x] Quyền đọc lịch sử được áp dụng theo quy tắc đã chốt ở N00.02.

Ghi nhận M05.01: `DoctorVisitDatabaseIntegrationTest` trên MySQL riêng kiểm tra khung được phân công, danh sách WAITING, bác sĩ khác không đọc/khởi động lượt, lịch sử chỉ qua lượt đang phụ trách và hai yêu cầu bắt đầu đồng thời giữ nguyên `started_at`. `DoctorVisitApiTest` kiểm tra role, CSRF và phân trang. M03 cung cấp hợp đồng đọc/khóa slot; M04 cung cấp định danh tối thiểu của bệnh nhân. Không có migration mới.

### M05.02 — Ghi nhận và kết thúc khám

**Nội dung gói công việc**

- Lưu ghi nhận khám, mã chẩn đoán nếu dùng, chẩn đoán, hướng xử trí và ngày khuyến nghị tái khám.
- Hoàn tất COMPLETED với điều kiện chẩn đoán, thời điểm hoàn thành. Ghi NOT_COMPLETED và lý do khi bỏ dở.
- Cập nhật kết quả lịch hẹn liên quan theo M03.04 trong giao dịch phù hợp.

**Mục tiêu gói công việc**

Lượt khám có kết quả chuyên môn và trạng thái kết thúc đầy đủ.

**Kết quả nghiệm thu**

- [ ] Hoàn tất thiếu chẩn đoán bị từ chối, bỏ dở thiếu lý do bị từ chối.
- [ ] Lượt và lịch hẹn liên quan nhất quán sau kết thúc hoặc khi xảy ra lỗi giữa giao dịch.
- [ ] Ngày khuyến nghị tái khám được lưu/hiển thị và không phát sinh suất giữ chỗ mới.

### M05.03 — Soạn và phát hành đơn thuốc

**Nội dung gói công việc**

- Tạo đơn DRAFT gắn lượt và bác sĩ, chọn thuốc từ M06.01, nhập lượng theo base_unit và hướng dẫn sử dụng.
- Kiểm tra lượng dương, thuốc được dùng, mỗi thuốc tối đa một dòng trong đơn.
- Chuyển ISSUED, ghi `issued_at` và cung cấp hợp đồng đọc đơn cho dược sĩ.

**Mục tiêu gói công việc**

Đơn thuốc đủ thông tin và điều kiện để chuyển sang cấp phát.

**Kết quả nghiệm thu**

- [ ] Đơn sai lượt/bác sĩ, dòng trùng thuốc hoặc lượng không hợp lệ bị chặn.
- [ ] Chỉ đơn ISSUED xuất hiện trong luồng cấp phát hợp lệ, phát hành lặp không tạo thêm đơn.
- [ ] Lập hoặc phát hành đơn giữ nguyên số dư kho, số dư chỉ đổi qua phiếu kho hợp lệ.

### M05.04 — Đơn bổ sung, hủy đơn và bảo vệ dữ liệu đã cấp

**Nội dung gói công việc**

- Cho tạo nhiều đơn trong một lượt khi cần bổ sung.
- Xác định điều kiện sửa/hủy theo trạng thái và dữ liệu cấp thuốc, yêu cầu lý do hủy.
- Phối hợp khóa/kiểm tra với M06 khi một yêu cầu sửa/hủy chạy cùng lúc với cấp phát.

**Mục tiêu gói công việc**

Điều chỉnh chỉ định có kiểm soát và bảo toàn căn cứ của các lần cấp thuốc.

**Kết quả nghiệm thu**

- [ ] Tạo đơn bổ sung giữ liên kết đúng lượt và bác sĩ, hủy thiếu lý do bị từ chối.
- [ ] Nội dung đã làm căn cứ cấp phát được bảo vệ, các thao tác xóa/sửa trái điều kiện bị chặn.
- [ ] Cạnh tranh giữa hủy đơn và ghi sổ cấp thuốc cho ra một trạng thái cuối hợp lệ, lượng kê và cấp đối chiếu được.

## 9. M06 — Quản lý dược và kho thuốc

### M06.01 — Danh mục thuốc và nhà cung cấp

**Nội dung gói công việc**

- Quản lý thuốc, hoạt chất, hàm lượng, dạng bào chế, đơn vị cơ sở, ngưỡng tồn, lead time và trạng thái.
- Quản lý nhà cung cấp và thông tin liên hệ, kiểm tra mã duy nhất.
- Bảo vệ đơn vị, hàm lượng và dạng bào chế của thuốc đã phát sinh nghiệp vụ.

**Mục tiêu gói công việc**

Kê đơn, nhập, xuất và báo cáo cùng sử dụng một danh mục và đơn vị số lượng.

**Kết quả nghiệm thu**

- [ ] Lượng kê/kho dùng base_unit, lead time âm hoặc ngưỡng tồn không hợp lệ bị từ chối.
- [ ] Thay bản chất thuốc đã có nghiệp vụ bị chặn và có hướng dẫn tạo mã mới.
- [ ] Danh mục ngừng hoạt động vẫn hiển thị đúng trong dữ liệu lịch sử.

### M06.02 — Lô thuốc, hạn dùng và khóa lô

**Nội dung gói công việc**

- Quản lý lô theo thuốc và số lô, hạn dùng, ACTIVE/BLOCKED và lý do khóa.
- Chốt cách xác định còn hạn tại ngày cấp theo timezone nghiệp vụ.
- Dùng thông tin lô cho các phiếu kho và kiểm tra điều kiện cấp.

**Mục tiêu gói công việc**

Mọi biến động và quyết định cấp phát truy được đến lô cụ thể.

**Kết quả nghiệm thu**

- [ ] Cùng thuốc không tạo trùng số lô, BLOCKED yêu cầu lý do.
- [ ] Lô khóa/hết hạn bị loại khỏi nguồn cấp, có kiểm tra ở ngày sát hạn và ngày hết hạn theo quy tắc đã chốt.

### M06.03 — Cấu trúc phiếu và cơ chế ghi sổ

**Nội dung gói công việc**

- Xây dựng phiếu và dòng phiếu, validation theo từng type, trạng thái DRAFT/POSTED/CANCELLED.
- Chốt bảng dấu cộng/trừ tồn theo loại phiếu, vị trí nguồn/đích và nhóm AVAILABLE/QUARANTINE.
- Xây dựng thao tác ghi sổ nguyên tử, lưu người/thời điểm, kiểm soát ghi sổ lặp, khóa nội dung POSTED.
- Áp dụng cơ chế kiểm tra tồn và khóa giao dịch dùng chung để các yêu cầu cạnh tranh không xuất quá tồn.

**Mục tiêu gói công việc**

Có một cơ chế ghi sổ đáng tin cậy cho mọi nghiệp vụ kho.

**Kết quả nghiệm thu**

- [ ] Chỉ POSTED tác động số dư, ghi sổ lại cùng phiếu không tác động tồn lần hai.
- [ ] Lỗi ở bất kỳ dòng nào rollback toàn phiếu, `posted_at` và `posted_by` chỉ phản ánh lần ghi sổ thành công.
- [ ] Hai phiếu cạnh tranh cùng lô/vị trí/nhóm tồn không làm tồn âm, kể cả khi số dư được tính từ lịch sử phiếu.
- [ ] Quy tắc hủy/sửa sai POSTED được chốt trước nghiệm thu, có kiểm tra tác động tới tồn, cấp phát và đề xuất nhập liên quan.

### M06.04 — Tồn đầu kỳ và nhập kho

**Nội dung gói công việc**

- Lập OPENING để đưa số dư ban đầu theo lô/vị trí/nhóm tồn.
- Lập RECEIPT với nhà cung cấp, lô, số lượng và giá nhập khi áp dụng.
- Chuẩn bị điểm kiểm tra đề xuất nhập cho M07.04, dùng cơ chế ghi sổ M06.03.

**Mục tiêu gói công việc**

Kho có số dư khởi tạo và ghi nhận được lượng hàng thực nhận.

**Kết quả nghiệm thu**

- [ ] Phiếu nhập thiếu nhà cung cấp hoặc số lượng không hợp lệ bị chặn.
- [ ] Số dư tăng đúng lô/vị trí/nhóm sau POSTED, phiếu nháp không tăng tồn.
- [ ] Gửi lặp yêu cầu ghi sổ không tăng tồn hai lần, dữ liệu đầu kỳ có ngày nghiệp vụ xác định.

### M06.05 — Chuyển kho và chuyển nhóm tồn

**Nội dung gói công việc**

- Lập TRANSFER giữa MAIN/COUNTER hoặc giữa AVAILABLE/QUARANTINE tại một điểm theo điều kiện xử lý.
- Kiểm tra nguồn/đích hợp lệ, lượng nguồn đủ và quyền chuyển thuốc cách ly về khả dụng.
- Ghi biến động nguồn và đích trong cùng transaction.

**Mục tiêu gói công việc**

Theo dõi đúng nơi giữ thuốc và trạng thái được phép sử dụng.

**Kết quả nghiệm thu**

- [ ] Chuyển thành công giảm nguồn và tăng đích đúng cùng lượng, tổng tồn vật lý toàn viện không đổi do chuyển.
- [ ] Nguồn/đích trùng hoàn toàn, nguồn thiếu hoặc điều kiện giải phóng cách ly không đạt bị từ chối.
- [ ] Lỗi giữa giao dịch không để số dư chỉ thay đổi ở một phía.

### M06.06 — Cấp thuốc theo đơn và theo lô

**Nội dung gói công việc**

- Đọc đơn ISSUED, tính lượng đã cấp và còn có thể cấp theo quy tắc đơn/thuốc.
- Tạo DISPENSE với từng lô ACTIVE, còn hạn, thuộc đúng thuốc và có AVAILABLE đủ tại điểm cấp.
- Cho một dòng đơn cấp từ nhiều lô, nhiều đợt. Kiểm tra dòng đơn thuộc đúng đơn của phiếu.
- Đồng bộ kiểm tra lượng kê và tồn khi nhiều dược sĩ cùng cấp một dòng đơn.

**Mục tiêu gói công việc**

Lượng thực cấp có nguồn lô, khớp đơn và đủ căn cứ tổng hợp dữ liệu sử dụng thuốc.

**Kết quả nghiệm thu**

- [ ] Đơn nháp/hủy, sai thuốc, sai dòng đơn, lô khóa/hết hạn/cách ly bị chặn.
- [ ] Ví dụ kê 10, đã cấp 6 thì lần cấp tiếp tối đa 4 theo quy tắc tổng cấp đã chốt. Hai yêu cầu đồng thời không vượt giới hạn.
- [ ] Một dòng cấp từ hai lô được lưu đúng chi tiết và trừ đúng số dư từng lô, lỗi một dòng rollback toàn phiếu.

### M06.07 — Ghi nhận phần thuốc chưa nhận

**Nội dung gói công việc**

- Cập nhật `unfilled_quantity`, lý do STOCKOUT/DECLINED/EXTERNAL/OTHER và ghi chú trên từng dòng đơn qua nghiệp vụ phối hợp M05.
- Lưu `dispensing_checked_at` ở đơn sau lần xử lý của dược sĩ, kiểm tra lượng chưa nhận phù hợp hiện trạng.
- Thể hiện rõ đơn chưa kiểm tra và đơn đã xử lý khi trả dữ liệu API.

**Mục tiêu gói công việc**

Phân biệt được tình trạng thiếu thuốc hiện tại với dữ liệu cấp phát đã phát sinh.

**Kết quả nghiệm thu**

- [ ] Lý do OTHER yêu cầu giải thích, lượng âm hoặc vượt giới hạn hợp lệ bị từ chối.
- [ ] Hai lần cập nhật lượng chưa nhận 4 rồi 2 cho hiện trạng cuối bằng 2.
- [ ] Đơn có lượng chưa nhận bằng 0 nhưng chưa có thời điểm kiểm tra vẫn thể hiện trạng thái chưa được dược sĩ xử lý.

### M06.08 — Nhận thuốc bệnh nhân trả

**Nội dung gói công việc**

- Tạo PATIENT_RETURN gắn đơn, dòng đơn, dòng cấp gốc và lô tương ứng.
- Kiểm tra tổng trả qua nhiều đợt không vượt lượng đã cấp từ dòng gốc, lưu lý do và tình trạng thuốc.
- Đưa thuốc trả vào QUARANTINE, áp dụng quy tắc cấp lại sau trả đã chốt ở N00.02.

**Mục tiêu gói công việc**

Truy nguyên thuốc trả và kiểm soát số lượng, tình trạng của thuốc quay lại kho.

**Kết quả nghiệm thu**

- [ ] Dòng gốc sai đơn/sai lô hoặc chưa ghi sổ cấp bị từ chối.
- [ ] Hai yêu cầu trả đồng thời không làm tổng trả vượt lượng gốc.
- [ ] POSTED làm tăng đúng tồn cách ly, tồn được xét cấp chỉ thay đổi sau nghiệp vụ xử lý phù hợp.

### M06.09 — Trả nhà cung cấp, hủy thuốc và kiểm kê

**Nội dung gói công việc**

- Tạo SUPPLIER_RETURN với đối tác nhận trả và DISPOSAL với lý do.
- Tạo ADJUSTMENT, lưu tồn sổ, lượng kiểm đếm và chênh lệch `counted_quantity - book_quantity`.
- Kiểm tra lại tồn khi ghi sổ kiểm kê để xử lý biến động phát sinh sau lúc đọc tồn sổ.

**Mục tiêu gói công việc**

Ghi nhận được các trường hợp giảm tồn và chênh lệch kiểm kê có căn cứ.

**Kết quả nghiệm thu**

- [ ] Trả thiếu nhà cung cấp, thiếu lý do hoặc vượt tồn nguồn bị chặn.
- [ ] Với tồn sổ 10 và kiểm đếm 8, quantity bằng -2 và tồn sau ghi sổ bằng 8 nếu không có biến động xen giữa.
- [ ] Có ca kiểm tra chênh lệch dương, âm, bằng 0 và thay đổi tồn xen giữa, mỗi ca cho kết quả theo quy tắc đã thống nhất.

### M06.10 — Tra cứu tồn và cảnh báo

**Nội dung gói công việc**

- Tổng hợp số dư từ phiếu hợp lệ theo thuốc, lô, vị trí, nhóm tồn.
- Tách số dư vật lý khỏi lượng được xét cấp khi lô khóa, hết hạn hoặc thuốc ở QUARANTINE.
- Cảnh báo dưới `minimum_stock` toàn viện và gần hết hạn với ngưỡng ngày được ghi rõ trong cấu hình triển khai.

**Mục tiêu gói công việc**

Dược sĩ có số tồn đối chiếu được để cấp phát và lập đề xuất nhập.

**Kết quả nghiệm thu**

- [ ] Bộ dữ liệu gồm nhập, chuyển, cấp, trả, hủy và điều chỉnh cho số dư đúng từng chiều.
- [ ] Tồn khả dụng loại đúng lô khóa/hết hạn và cách ly, cảnh báo thiếu xét ngưỡng theo toàn viện.
- [ ] Số dư chi tiết cộng lên khớp tổng hợp và truy được các phiếu tạo ra biến động.

## 10. M07 — Đề xuất nhập thuốc

### M07.01 — Lập và gửi đề xuất

**Nội dung gói công việc**

- PHARMACIST tạo DRAFT, chọn thuốc, lượng theo base_unit, nhà cung cấp dự kiến và căn cứ.
- Kiểm tra mỗi thuốc một dòng, lượng dương, chuyển PENDING khi gửi duyệt.
- Áp dụng điều kiện sửa/hủy trước khi duyệt và ghi audit.

**Mục tiêu gói công việc**

Nhu cầu nhập thuốc được lập thành dữ liệu có thể phê duyệt và đối chiếu.

**Kết quả nghiệm thu**

- [ ] Thuốc trùng, lượng không hợp lệ và chuyển trạng thái trái điều kiện bị chặn.
- [ ] Dược sĩ tạo/gửi được đề xuất và không tự thực hiện bước phê duyệt.

### M07.02 — Duyệt hoặc từ chối một bước

**Nội dung gói công việc**

- ADMIN duyệt PENDING sang APPROVED hoặc từ chối sang REJECTED.
- Lưu người, thời điểm và nội dung giải thích theo trường hợp. Duyệt toàn bộ lượng trên các dòng.
- Khóa nội dung lượng đã duyệt, xử lý yêu cầu duyệt/từ chối lặp hoặc đồng thời.

**Mục tiêu gói công việc**

Có quyết định nhập rõ ràng làm căn cứ cho ghi nhận hàng.

**Kết quả nghiệm thu**

- [ ] Chỉ ADMIN duyệt/từ chối, thông tin người và thời điểm phản ánh quyết định thành công.
- [ ] Hai quyết định cạnh tranh không ghi đè lẫn nhau, sửa lượng đã duyệt bị chặn.

### M07.03 — Theo dõi đặt hàng và kết thúc

**Nội dung gói công việc**

- Ghi `ordered_at`, `expected_delivery_date` và hiển thị tình trạng còn theo dõi của đề xuất.
- Kết thúc qua `closed_at`, yêu cầu giải thích khi dừng nhận chưa đủ lượng.
- Ghi điều kiện hủy/kết thúc khi đã có phiếu nhập liên quan và xử lý phiếu nháp còn tồn tại.

**Mục tiêu gói công việc**

Theo dõi được tiến trình từ đề xuất đã duyệt đến lúc dừng nhận hàng.

**Kết quả nghiệm thu**

- [ ] Ngày đặt và dự kiến nhận được kiểm tra theo quy tắc thời gian đã thống nhất.
- [ ] Đóng khi thiếu hàng có lý do, đề xuất đã đóng bị kiểm tra khi yêu cầu ghi sổ nhận thêm.
- [ ] Lịch sử nhận đã ghi sổ vẫn đối chiếu được sau khi kết thúc theo dõi.

### M07.04 — Nhận hàng nhiều đợt và đối chiếu lượng duyệt

**Nội dung gói công việc**

- Phối hợp RECEIPT của M06, kiểm tra đề xuất APPROVED, nhà cung cấp đúng và thuốc có trong đề xuất.
- Tính đã nhận/còn thiếu từ các phiếu nhập hợp lệ theo từng thuốc.
- Khóa/kiểm tra trong giao dịch để hai phiếu nhận đồng thời không vượt lượng duyệt.

**Mục tiêu gói công việc**

Nhận hàng theo đề xuất kiểm soát được cả nhiều lô và nhiều đợt.

**Kết quả nghiệm thu**

- [ ] Ví dụ duyệt 100, nhận 40 rồi 60 thì đã nhận 100, còn thiếu 0. Phiếu nhận vượt bị từ chối.
- [ ] Sai nhà cung cấp, sai thuốc, đề xuất chưa duyệt hoặc đã đóng bị từ chối theo điều kiện đã chốt.
- [ ] Phiếu nháp không cộng lượng đã nhận, hai phiếu đồng thời tuân giới hạn và đối chiếu khớp tồn kho.

## 11. M08 — Báo cáo và thống kê

### M08.01 — Báo cáo lịch hẹn và lượt đến

**Nội dung gói công việc**

- Tổng hợp lịch hẹn theo thời gian, trạng thái, bác sĩ và phòng.
- Tổng hợp lượt đến theo ngày `arrived_at`, chuyên khoa, phòng, bác sĩ. Dùng timezone nghiệp vụ và đếm mỗi medical_visit một lần.
- Áp dụng phạm vi ADMIN, RECEPTIONIST và DOCTOR theo trách nhiệm.

**Mục tiêu gói công việc**

Người vận hành theo dõi được lịch đăng ký và số người thực sự đến.

**Kết quả nghiệm thu**

- [ ] Dữ liệu hẹn chưa đến chỉ xuất hiện ở số hẹn, NOT_COMPLETED được tính lượt đến, VOIDED được loại.
- [ ] Join với nhiều đơn thuốc không làm nhân số lượt, ca qua ranh giới ngày được xếp đúng ngày nghiệp vụ.
- [ ] DOCTOR chỉ nhận báo cáo công việc thuộc phạm vi được phân công.

### M08.02 — Đối chiếu kê, cấp và chưa nhận

**Nội dung gói công việc**

- Tổng hợp lượng kê theo đơn hợp lệ, lượng cấp theo DISPENSE POSTED và hiện trạng chưa nhận theo lý do.
- Hiển thị điều kiện lọc, khoảng thời gian và định nghĩa chỉ số rõ ràng, phân biệt thời điểm kê với thời điểm cấp.
- Cho truy xuống đơn, dòng cấp và lần kiểm tra gần nhất để giải thích số liệu.

**Mục tiêu gói công việc**

Đối chiếu được chỉ định và lượng thuốc đã thực sự cấp.

**Kết quả nghiệm thu**

- [ ] Một dòng kê được cấp qua nhiều lô/đợt vẫn có tổng đúng, phiếu trả/chuyển không bị cộng vào thực cấp.
- [ ] Hiện trạng chưa nhận được thể hiện là dữ liệu gần nhất và không bị cộng qua các lần cập nhật.
- [ ] Lượng chưa nhận bằng 0 ở đơn chưa kiểm tra không được diễn giải thành đã cấp đủ.

### M08.03 — Báo cáo kho và đề xuất nhập

**Nội dung gói công việc**

- Tổng hợp tồn, biến động theo thuốc/lô/vị trí/nhóm và các loại phiếu trong khoảng thời gian.
- Báo cáo đề xuất chờ duyệt, đã duyệt, dự kiến nhận và lượng còn thiếu.
- Dùng chung định nghĩa tồn M06 và đã nhận M07, áp dụng quyền ADMIN/PHARMACIST.

**Mục tiêu gói công việc**

Quản trị và dược sĩ sử dụng số liệu nhất quán với màn hình nghiệp vụ.

**Kết quả nghiệm thu**

- [ ] Báo cáo khớp số dư và chi tiết phiếu, chuyển nội bộ không làm sai tổng tồn toàn viện.
- [ ] Lượng nhận/còn thiếu khớp từng đề xuất và xử lý đúng phiếu nháp, phiếu bị hủy theo quy tắc đã chốt.
- [ ] Role ngoài phạm vi bị từ chối truy cập báo cáo dược.

## 12. M09 — Dữ liệu demo và học máy

### M09.01 — Chuẩn bị và import dữ liệu demo

**Nội dung gói công việc**

- Chuẩn bị dữ liệu giả lập có thứ tự phụ thuộc từ tài khoản, danh mục, lịch, patient đến lượt, đơn và kho.
- Xây dựng script/quy trình import giữ đúng ngày nghiệp vụ lịch sử, lượng và liên kết. Ghi cách chạy lại trên database demo riêng.
- Có các tình huống hẹn và trực tiếp, bỏ dở, nhập nhầm, cấp nhiều lô, thiếu thuốc, trả và nhận hàng nhiều đợt.

**Mục tiêu gói công việc**

Có dữ liệu tái lập để demo, đối chiếu nghiệp vụ và tạo dataset.

**Kết quả nghiệm thu**

- [ ] Import trên database demo mới thành công, kiểm tra được FK, enum, số lượng và số dư.
- [ ] Ngày phát sinh lịch sử được giữ đúng, thời điểm import không thay thế `arrived_at` hoặc `occurred_at`.
- [ ] Chạy lại theo hướng dẫn không âm thầm nhân bản dữ liệu, có danh sách các tình huống đã tạo.

### M09.02 — Kiểm tra chất lượng và xuất dataset

**Nội dung gói công việc**

- Kiểm tra dữ liệu thiếu, trùng, thời gian, số lượng và khoảng lịch sử đầy đủ trước tổng hợp.
- Xuất chuỗi lượt đến theo ngày arrived_at, loại VOIDED. Xuất lượng thuốc thực cấp từ DISPENSE POSTED theo occurred_at và medicine qua lô.
- Ghi schema tệp, đơn vị, timezone, khoảng dữ liệu và cách xử lý ngày không phát sinh so với ngày thiếu dữ liệu.

**Mục tiêu gói công việc**

Tạo đầu vào có thể đối chiếu và sử dụng nhất quán trong hai bài toán ML.

**Kết quả nghiệm thu**

- [ ] Tổng dataset lượt đến khớp M08.01, lượng thuốc khớp M08.02 trên cùng điều kiện lọc.
- [ ] Phiếu chuyển, trả, hủy thuốc không bị cộng vào thực cấp, đơn chưa cấp không tạo lượng sử dụng.
- [ ] Tệp có mô tả cột và kiểm tra ngày thiếu, dữ liệu định danh bệnh nhân không được đưa vào đầu vào dự báo vận hành.

### M09.03 — Hợp đồng trao đổi với thành phần ML

**Nội dung gói công việc**

- Chọn cách demo trao đổi qua tệp hoặc kết quả trả về từ thành phần ML, ghi quyết định trước khi code tích hợp.
- Định nghĩa đầu ra lượt khám gồm ngày và số lượt dự báo, đầu ra thuốc gồm mã thuốc, ngày/kỳ, lượng và đơn vị cơ sở.
- Bổ sung thông tin ngữ cảnh như thời điểm tạo, khoảng dữ liệu huấn luyện/kỳ dự báo để người xem hiểu kết quả. Xác định phản hồi khi chưa có kết quả hoặc dữ liệu sai.

**Mục tiêu gói công việc**

Backend và thành phần ML có hợp đồng cụ thể để triển khai độc lập.

**Kết quả nghiệm thu**

- [ ] Có tệp/hợp đồng mẫu cho hai bài toán, mẫu hợp lệ và mẫu lỗi dùng chung cho hai phía.
- [ ] Mã thuốc lạ, đơn vị không khớp, ngày/giá trị không hợp lệ được phát hiện theo hợp đồng.
- [ ] Cách dùng tệp mô hình/kết quả phù hợp phạm vi 18 bảng, nhu cầu lưu lịch sử dự báo lâu dài được ghi là mở rộng riêng.

### M09.04 — Chuẩn bị mô hình phục vụ nghiệm thu tích hợp

**Nội dung gói công việc**

- Phối hợp thành phần ML nhận dataset đã kiểm tra, chọn thuật toán trong bước nghiên cứu, chia tập theo thời gian.
- Yêu cầu đầu ra đánh giá trên giai đoạn chưa dùng huấn luyện, so sánh với dự báo cơ sở và ghi hạn chế dữ liệu.
- Cung cấp kết quả dự báo mẫu thật theo hợp đồng M09.03, số lượt khám cho một đến hai tuần và thuốc theo kỳ đã chọn.

**Mục tiêu gói công việc**

Có mô hình và kết quả thực tế để nghiệm thu phần kết nối backend với ML.

Đây là gói phối hợp với thành phần ML. Công việc huấn luyện nằm tại thành phần xử lý ML, công việc backend là chuẩn bị dữ liệu, hợp đồng và kiểm tra kết quả nhận vào. Công nghệ/thuật toán ML cụ thể được xác định trong bước nghiên cứu.

**Kết quả nghiệm thu**

- [ ] Có dataset/version nguồn, khoảng train/test, cách chạy và kết quả đánh giá tái lập được.
- [ ] Có kết quả đúng hợp đồng cho cả hai bài toán, ghi rõ hạn chế do thiếu thuốc có thể làm lượng thực cấp thấp hơn nhu cầu.
- [ ] Ngưỡng chất lượng mô hình được quyết định theo dữ liệu và báo cáo nghiên cứu trước khi nghiệm thu chất lượng dự báo. Backend chỉ nghiệm thu kỹ thuật tích hợp khi có đầu ra hợp lệ.

### M09.05 — Cung cấp dự báo cho frontend

**Nội dung gói công việc**

- Đọc/nhận kết quả theo cách đã chốt, cung cấp API để ADMIN xem lượt khám và ADMIN/PHARMACIST xem thuốc.
- Với dự báo thuốc, cung cấp dữ liệu tồn khả dụng và lead time để dược sĩ đối chiếu khi lập đề xuất.
- Xử lý trường hợp kết quả chưa có, sai định dạng hoặc thuộc kỳ cũ, trả trạng thái rõ cho frontend.

**Mục tiêu gói công việc**

Người có quyền sử dụng kết quả dự báo trong quyết định vận hành.

**Kết quả nghiệm thu**

- [ ] Đọc được kết quả thật từ M09.04 cho cả hai bài toán và áp dụng đúng quyền.
- [ ] Thiếu/lỗi kết quả có phản hồi xác định, các API nghiệp vụ khám và kho vẫn sử dụng được.
- [ ] Kết quả hiển thị đúng kỳ, mã thuốc và đơn vị, dữ liệu dự báo không tự tạo lịch khám hoặc đề xuất nhập.

## 13. N10 — Tích hợp, đóng gói và bàn giao

### N10.01 — Kiểm thử xuyên module và quyền hệ thống

**Nội dung gói công việc**

- Chạy luồng online từ đăng ký, đặt hẹn, tiếp nhận, khám, kê đơn, cấp thuốc đến xem lịch sử và báo cáo.
- Chạy luồng trực tiếp, liên kết tài khoản sau đó, đơn bổ sung, thuốc thiếu/trả, đề xuất nhập nhiều đợt.
- Kiểm tra từng role, truy cập chéo patient/doctor, yêu cầu lặp, yêu cầu đồng thời và lỗi giữa transaction tại các điểm quan trọng.

**Mục tiêu gói công việc**

Chứng minh các module hoạt động cùng nhau và dữ liệu cuối cùng đối chiếu được.

**Kết quả nghiệm thu**

- [ ] Có biên bản chạy từng luồng với dữ liệu, kết quả mong đợi/thực tế và lỗi đã xử lý.
- [ ] Các ca cạnh tranh giữ suất, cấp số, ghi sổ, cấp thuốc, trả thuốc và nhận hàng vượt lượng đều đạt.
- [ ] Kết quả API, database, báo cáo và dataset khớp trên cùng bộ dữ liệu kiểm thử.

### N10.02 — Đóng gói và chạy môi trường triển khai

**Nội dung gói công việc**

- Tạo Dockerfile cho backend và Docker Compose cho backend/MySQL, cấu hình bên ngoài image, lưu dữ liệu MySQL bằng volume.
- Ghi thứ tự khởi động, chờ database sẵn sàng, chạy migration và cách xác minh ứng dụng đã phục vụ API.
- Chuẩn bị cấu hình địa chỉ frontend/API, session, kết nối trình duyệt và các yêu cầu tại môi trường chạy thực tế. Chọn đích triển khai khi đến giai đoạn phát hành.

**Mục tiêu gói công việc**

Chạy được bản backend đóng gói cùng database trong môi trường tái lập.

**Kết quả nghiệm thu**

- [ ] Máy có các điều kiện chạy đã ghi trong hướng dẫn khởi động được hệ thống từ image/cấu hình bàn giao.
- [ ] Restart hoặc thay container ứng dụng giữ được dữ liệu MySQL trong volume.
- [ ] Frontend gọi được luồng đăng nhập và nghiệp vụ qua địa chỉ triển khai, cấu hình local không bị hardcode vào artifact.

### N10.03 — Diễn tập build, migration và deploy phiên bản mới

**Nội dung gói công việc**

- Ghi quy trình lấy phiên bản code, build, chạy kiểm thử, gắn phiên bản image, sao lưu dữ liệu và triển khai.
- Diễn tập nâng cấp một thay đổi có migration trên bản sao dữ liệu, xác minh dữ liệu cũ cùng nghiệp vụ mới.
- Ghi điều kiện quay lại image cũ khi schema tương thích. Với schema không tương thích, xác định cách khôi phục bản sao dữ liệu và ứng dụng tương ứng.

**Mục tiêu gói công việc**

Người dùng thực hành được chu kỳ maintenance từ sửa code đến cập nhật hệ thống đang có dữ liệu.

**Kết quả nghiệm thu**

- [ ] Có một lần nâng cấp thử từ phiên bản A sang B, dữ liệu cũ còn sử dụng được và chức năng thay đổi chạy đúng.
- [ ] Có bản sao lưu và kết quả khôi phục đã kiểm tra, mô tả ảnh hưởng đến dữ liệu phát sinh sau thời điểm sao lưu.
- [ ] Hướng dẫn phân biệt rollback ứng dụng với khôi phục database và ghi rõ điều kiện áp dụng từng cách.

### N10.04 — Bàn giao API, hướng dẫn và tiến độ

**Nội dung gói công việc**

- Tổng hợp hợp đồng API, vai trò, dữ liệu mẫu, cấu hình, lệnh build/chạy và các bước kiểm tra bản triển khai.
- Cập nhật trạng thái từng mã gói, quyết định kỹ thuật, hạn chế đã biết và phần ML còn phụ thuộc nếu có.
- Ghi cách giao một gói mới cho Agent, yêu cầu báo cáo thay đổi, kết quả kiểm thử và cập nhật tài liệu khi maintenance.

**Mục tiêu gói công việc**

Người dùng bám sát tiến độ, chạy lại dự án và tiếp tục phát triển bằng Agent.

**Kết quả nghiệm thu**

- [ ] Người dùng có thể làm theo hướng dẫn để build, chạy, gọi các luồng chính và thực hiện nâng cấp thử.
- [ ] Mỗi gói đã hoàn thành có commit hoặc bằng chứng bàn giao và kết quả nghiệm thu, các gói chưa đạt có lý do cụ thể.
- [ ] Phạm vi bản phát hành và những quyết định khác tài liệu nguồn được ghi rõ, có thể truy ngược tới gói xử lý.

## 14. Mẫu ghi nhận cho mỗi gói

Sao chép mẫu này vào issue hoặc phần theo dõi của gói đang thực hiện.

| Mục theo dõi               | Nội dung cần điền                                                    |
| -------------------------- | -------------------------------------------------------------------- |
| Mã gói                     | Mã trong plan                                                        |
| Trạng thái                 | Chưa làm / Đang làm / Chờ phụ thuộc / Chờ nghiệm thu / Đã nghiệm thu |
| Người hoặc Agent phụ trách | Tên người thực hiện                                                  |
| Gói tiên quyết             | Mã gói và phần đầu ra cần sử dụng                                    |
| Phạm vi thay đổi           | Package, API, bảng và migration dự kiến                              |
| Quyết định cần chốt        | Điểm nghiệp vụ còn chưa rõ và người xác nhận                         |
| Bằng chứng hoàn thành      | Commit, tài liệu API, lệnh kiểm tra và kết quả thực tế               |
| Kết quả nghiệm thu         | Các checklist đã đạt, chưa đạt và lý do                              |
| Công việc tiếp theo        | Gói có thể bắt đầu sau khi nghiệm thu                                |
