# Thiết kế database HTAC

## Các trường dùng chung

| Tên trường | Kiểu dữ liệu | Mô tả ngắn |
| --- | --- | --- |
| created_at | timestamptz | Thời điểm tạo bản ghi, mặc định thời điểm hiện tại; bắt buộc. |
| updated_at | timestamptz | Thời điểm cập nhật gần nhất; bắt buộc, ban đầu bằng created_at. |
| created_by | bigint | FK đến user_account. Người tạo; cho phép NULL khi khởi tạo hoặc hệ thống tự tạo. |
| updated_by | bigint | FK đến user_account. Người sửa gần nhất; cho phép NULL khi thao tác do hệ thống. |

## Các bảng dữ liệu

### 1 Tài khoản

user_account — Lưu thông tin đăng nhập của bệnh nhân và nhân viên.

| Tên trường | Kiểu dữ liệu | Mô tả ngắn |
| --- | --- | --- |
| id | bigint | Khóa chính tự tăng. |
| username | varchar(50) | Tên đăng nhập duy nhất. |
| password_hash | varchar(255) | Mật khẩu đã băm; không lưu mật khẩu gốc. |
| full_name | varchar(150) | Tên hiển thị của chủ tài khoản. |
| phone | varchar(20) | Số điện thoại liên hệ. |
| role | varchar(20) | ADMIN, RECEPTIONIST, DOCTOR, PHARMACIST hoặc PATIENT. |
| is_active | boolean | Tài khoản được phép đăng nhập; mặc định true. |
| credential_version | bigint | Phiên bản thông tin xác thực; tăng khi đổi/đặt lại mật khẩu để hủy phiên cũ. |

Bệnh nhân tự đăng ký chỉ được gán PATIENT. ADMIN tạo tài khoản nhân viên. Khóa tài khoản không xóa dữ liệu khám đã liên kết.

`account_password_reset_audit` — Audit bất biến cho lần ADMIN đặt lại mật khẩu sau khi đối chiếu danh tính trong bản demo. Bảng bổ sung ở migration V3, ngoài 18 bảng nghiệp vụ nền.

| Tên trường | Kiểu dữ liệu | Mô tả ngắn |
| --- | --- | --- |
| id | bigint | Khóa chính tự tăng. |
| target_user_id | bigint | FK đến user_account được đặt lại mật khẩu. |
| admin_user_id | bigint | FK đến ADMIN thực hiện. |
| verified_via | varchar(20) | IN_PERSON hoặc PHONE_CALLBACK. |
| created_at | timestamptz | Thời điểm đặt lại. |

### 2 Hồ sơ bệnh nhân

patient — Lưu hồ sơ định danh độc lập với tài khoản, phục vụ cả người đăng ký trực tiếp và người đặt khám online.

| Tên trường | Kiểu dữ liệu | Mô tả ngắn |
| --- | --- | --- |
| id | bigint | Khóa chính tự tăng. |
| patient_code | varchar(30) | Mã bệnh nhân duy nhất do bệnh viện cấp. |
| user_id | bigint | FK đến user_account, duy nhất. Cho phép NULL khi chưa có tài khoản. |
| citizen_id | varchar(12) | Số CCCD duy nhất khi có. Cho phép NULL với hồ sơ tiếp nhận trực tiếp chưa có CCCD. |
| full_name | varchar(150) | Họ và tên người bệnh. |
| date_of_birth | date | Ngày sinh. |
| gender | varchar(10) | MALE, FEMALE hoặc OTHER. |
| address | text | Địa chỉ liên hệ. Cho phép NULL. |
| registration_token_hash | varchar(64) | SHA-256 của mã xác minh liên kết do tiếp đón cấp; cho phép NULL. |
| registration_token_expires_at | timestamptz | Hạn của mã xác minh; cho phép NULL và cùng NULL/khác NULL với hash. |

### 3 Hồ sơ bác sĩ

doctor_profile - Lưu hồ sơ của bác sĩ

| Tên trường | Kiểu dữ liệu | Mô tả ngắn |
| --- | --- | --- |
| user_id | bigint | Khóa chính, đồng thời là khóa ngoại đến user_account.id. Lấy giá trị từ tài khoản bác sĩ, không tự tăng |
| specialty_id | bigint | Khóa ngoại đến specialty_id. Chuyên khoa của bác sĩ |
| avatar_url | varchar(500) | Đường dẫn ảnh đại diện bác sĩ |
| professional_title | varchar(100) | Học hàm, học vị hoặc chức danh chuyên môn để hiển thị, ví dụ: ThS.BS, TS.BS, … |
| position | varchar(150) | Chức vụ tại bệnh viện, ví dụ: Bác sĩ khám bệnh, Phó trưởng khoa, Trưởng khoa. |
| practice_start_year | smallint | Năm bắt đầu hành nghề. Dùng tính số năm kinh nghiệm ước tính. |
| introduction | text | Giới thiệu ngắn về bác sĩ và định hướng chuyên môn. |
| clinical_expertise | text | Lĩnh vực chuyên sâu, nhóm bệnh tiếp nhận khám và các kỹ thuật chuyên môn |
| certifications | text | Chứng chỉ và các khóa đào tạo chuyên môn bổ sung. Cho phép null |
| is_public | boolean | Hồ sơ có được hiển thị cho bệnh nhân hay không. Admin công khai sau khi hoàn thành hồ sơ |

### 4 Chuyên khoa

specialty — Danh mục để lọc phòng khám, phân công bác sĩ và tổng hợp lượt khám.

| Tên trường | Kiểu dữ liệu | Mô tả ngắn |
| --- | --- | --- |
| id | bigint | Khóa chính tự tăng. |
| code | varchar(20) | Mã chuyên khoa duy nhất. |
| name | varchar(100) | Tên chuyên khoa. |
| is_active | boolean | Đang sử dụng; mặc định true. |

### 5 Phòng khám

clinic_room — Mỗi phòng thuộc một chuyên khoa trong phạm vi tối giản.

| Tên trường | Kiểu dữ liệu | Mô tả ngắn |
| --- | --- | --- |
| id | bigint | Khóa chính tự tăng. |
| specialty_id | bigint | FK đến specialty. Chuyên khoa phục vụ. |
| code | varchar(20) | Mã phòng duy nhất. |
| name | varchar(100) | Tên phòng hiển thị khi đặt khám. |
| location | varchar(200) | Tầng, tòa nhà hoặc vị trí phòng. Cho phép NULL. |
| is_active | boolean | Phòng đang sử dụng; mặc định true. |

Phòng đã phát sinh lịch sử không đổi chuyên khoa. Khi tổ chức lại, tạo danh mục phòng mới để giữ đúng thống kê quá khứ.

### 6 Khung giờ khám

clinic_slot — Một dòng là một khung giờ cụ thể của một phòng và một bác sĩ, ví dụ ngày 10 tháng 11 từ 08 giờ đến 08 giờ 15.

| Tên trường | Kiểu dữ liệu | Mô tả ngắn |
| --- | --- | --- |
| id | bigint | Khóa chính tự tăng. |
| room_id | bigint | FK đến clinic_room. Phòng phục vụ. |
| doctor_id | bigint | FK đến doctor_profile.user_id |
| start_at | timestamptz | Ngày giờ bắt đầu khung khám. |
| end_at | timestamptz | Ngày giờ kết thúc, phải sau start_at. |
| capacity | integer | Số bệnh nhân tối đa; lớn hơn 0. |
| status | varchar(10) | OPEN: nhận đăng ký; CLOSED: ngừng nhận thêm. |

ADMIN có thể tạo nhiều khung giờ theo một buổi. Không xếp trùng giờ cho cùng phòng hoặc cùng bác sĩ. Khung đã có lượt thực tế được giữ nguyên thông tin; CLOSED vẫn giữ các lịch hẹn đang có để nhân viên xử lý.

### 7 Lịch hẹn khám online

appointment

| Tên trường | Kiểu dữ liệu | Mô tả ngắn |
| --- | --- | --- |
| id | bigint | Khóa chính tự tăng. |
| appointment_code | varchar(30) | Mã lịch hẹn duy nhất. |
| patient_id | bigint | FK đến patient. Người đặt khám cho bản thân. |
| slot_id | bigint | FK đến clinic_slot. Phòng, bác sĩ và thời gian đã chọn. |
| reason | text | Lý do khám do bệnh nhân nhập. |
| status | varchar(20) | PENDING, CONFIRMED, COMPLETED hoặc NOT_COMPLETED. |
| confirmed_at | timestamptz | Thời điểm nhân viên xác nhận qua điện thoại. Cho phép NULL. |
| confirmed_by | bigint | FK đến user_account. Nhân viên xác nhận. Cho phép NULL. |
| closed_at | timestamptz | Thời điểm chuyển sang trạng thái kết thúc. Cho phép NULL. |
| non_completion_reason | text | Lý do không hoàn thành; bắt buộc khi NOT_COMPLETED. Trường hợp khác cho phép NULL. |

Ngày giờ khám lấy từ clinic_slot; ngày tạo lịch lấy từ created_at. Chỉ tạo medical_visit khi bệnh nhân thực sự đến và được tiếp nhận. Lịch hẹn chưa đến không được tính là lượt khám thực tế.

### 8 Lượt khám và kết quả khám

medical_visit — Một dòng là một lượt đã được tiếp nhận tại bệnh viện, gồm cả người có hẹn và người đăng ký trực tiếp. Nội dung khám lưu ngay tại đây.

| Tên trường | Kiểu dữ liệu | Mô tả ngắn |
| --- | --- | --- |
| id | bigint | Khóa chính tự tăng. |
| visit_code | varchar(30) | Mã lượt khám duy nhất. |
| patient_id | bigint | FK đến patient. Bệnh nhân được tiếp nhận. |
| appointment_id | bigint | FK đến appointment, duy nhất. Cho phép NULL với người đến trực tiếp. |
| slot_id | bigint | FK đến clinic_slot. Phòng, bác sĩ và khung phục vụ thực tế. |
| queue_number | integer | Số thứ tự tại phòng trong ngày; lớn hơn 0. |
| arrived_at | timestamptz | Thời điểm tiếp nhận thực tế; dùng đếm lượt đến. |
| started_at | timestamptz | Thời điểm bắt đầu khám. Cho phép NULL khi chưa khám. |
| completed_at | timestamptz | Thời điểm hoàn thành khám. Cho phép NULL khi chưa hoàn thành. |
| status | varchar(20) | WAITING, IN_PROGRESS, COMPLETED, NOT_COMPLETED hoặc VOIDED. |
| reason | text | Lý do khám; có thể sao chép từ lịch hẹn. |
| clinical_notes | text | Triệu chứng, tiền sử, dị ứng, chỉ số cơ bản và ghi nhận khám. Cho phép NULL khi chưa khám. |
| diagnosis_code | varchar(20) | Mã chẩn đoán chính nếu có sử dụng. Cho phép NULL. |
| diagnosis | text | Chẩn đoán hoặc kết luận; bắt buộc khi COMPLETED. Trước đó cho phép NULL. |
| treatment_plan | text | Hướng xử trí và lời dặn. Cho phép NULL. |
| follow_up_date | date | Ngày khuyến nghị tái khám; chưa phải lịch hẹn mới. Cho phép NULL. |
| outcome_reason | text | Lý do bỏ dở hoặc nhập nhầm; bắt buộc khi NOT_COMPLETED hoặc VOIDED. Còn lại cho phép NULL. |

WAITING là chờ khám; IN_PROGRESS là đang khám; NOT_COMPLETED là đã đến nhưng không hoàn tất; VOIDED chỉ dùng cho bản ghi nhập nhầm. Người bỏ về vẫn là một lượt đến. Không tính VOIDED. Cấp số thứ tự không trùng trong cùng phòng và ngày.

### 9 Đơn thuốc

prescription — Lưu phần thông tin chung của đơn. Một lượt có thể có nhiều đơn nếu cần bổ sung.

| Tên trường | Kiểu dữ liệu | Mô tả ngắn |
| --- | --- | --- |
| id | bigint | Khóa chính tự tăng. |
| prescription_code | varchar(30) | Mã đơn thuốc duy nhất. |
| visit_id | bigint | FK đến medical_visit. Lượt khám phát sinh đơn. |
| doctor_id | bigint | FK đến doctor_profile.user_id. Bác sĩ kê đơn. |
| issued_at | timestamptz | Thời điểm bác sĩ xác nhận đơn. Cho phép NULL khi còn nháp. |
| status | varchar(15) | DRAFT: đang soạn; ISSUED: đã xác nhận; CANCELLED: hủy đơn. |
| note | text | Lời dặn chung hoặc lý do hủy. Cho phép NULL; bắt buộc nếu hủy. |
| dispensing_checked_at | timestamptz | Lần gần nhất dược sĩ ghi nhận việc nhận thuốc hoặc thiếu thuốc. Cho phép NULL. |

Chỉ đơn ISSUED được cấp phát. Đơn đã cấp thuốc không được xóa hoặc sửa phần đã cấp. Bản gọn lưu đơn bổ sung riêng khi cần; không quản lý phiên bản từng dòng đơn.

### 10 Chi tiết đơn thuốc

prescription_item — Mỗi dòng là một thuốc được chỉ định, cùng lượng kê và phần chưa nhận đã được dược sĩ ghi nhận.

| Tên trường | Kiểu dữ liệu | Mô tả ngắn |
| --- | --- | --- |
| id | bigint | Khóa chính tự tăng. |
| prescription_id | bigint | FK đến prescription. Đơn chứa dòng thuốc. |
| medicine_id | bigint | FK đến medicine. Thuốc được kê. |
| quantity | numeric(14,3) | Lượng kê theo đơn vị cơ sở của thuốc; lớn hơn 0. |
| usage_instruction | text | Liều mỗi lần, số lần, đường dùng, số ngày và hướng dẫn sử dụng. |
| unfilled_quantity | numeric(14,3) | Lượng chưa nhận tại lần kiểm tra gần nhất; mặc định 0 trước khi kiểm tra. |
| unfilled_reason | varchar(20) | STOCKOUT, DECLINED, EXTERNAL hoặc OTHER. Cho phép NULL nếu không còn thiếu. |
| unfilled_note | text | Giải thích phần chưa nhận. Cho phép NULL; bắt buộc nếu lý do OTHER. |

unfilled_quantity là hiện trạng, không phải các sự kiện được cộng dồn. Dược sĩ cập nhật sau từng lần xử lý nhận thuốc; 0 trước lần kiểm tra đầu không có nghĩa đã cấp đủ. Lượng thực cấp lấy từ stock_document_item. Mỗi thuốc xuất hiện tối đa một dòng trong một đơn.

### 11 Danh mục thuốc

medicine — Mỗi mã đại diện một thuốc với hoạt chất, hàm lượng, dạng bào chế và đơn vị cơ sở xác định.

| Tên trường | Kiểu dữ liệu | Mô tả ngắn |
| --- | --- | --- |
| id | bigint | Khóa chính tự tăng. |
| code | varchar(30) | Mã thuốc duy nhất. |
| name | varchar(150) | Tên thuốc hoặc tên thương mại. |
| active_ingredient | varchar(200) | Hoạt chất; có thể ghi nhiều hoạt chất trong cùng trường. |
| strength | varchar(100) | Hàm lượng hoặc nồng độ. |
| dosage_form | varchar(50) | Dạng bào chế, ví dụ viên nén hoặc dung dịch. |
| base_unit | varchar(20) | Đơn vị duy nhất dùng để kê và tính tồn, ví dụ viên, chai hoặc ống. |
| minimum_stock | numeric(14,3) | Ngưỡng cảnh báo tồn khả dụng toàn viện; mặc định 0. |
| lead_time_days | integer | Số ngày dự kiến chờ nhập thuốc; không âm. |
| is_active | boolean | Thuốc còn được sử dụng; mặc định true. |

Bản gọn nhập mọi số lượng theo base_unit; người nhập tự quy đổi hộp, vỉ trước khi ghi. Không đổi đơn vị, hàm lượng hoặc dạng bào chế của mã đã phát sinh nghiệp vụ; tạo mã mới nếu thay đổi bản chất thuốc.

### 12 Lô thuốc

medicine_batch — Giữ thông tin lô và hạn dùng. Tồn của lô tại từng điểm được tính từ phiếu kho.

| Tên trường | Kiểu dữ liệu | Mô tả ngắn |
| --- | --- | --- |
| id | bigint | Khóa chính tự tăng. |
| medicine_id | bigint | FK đến medicine. Thuốc thuộc lô. |
| batch_number | varchar(60) | Số lô của nhà sản xuất; duy nhất trong cùng medicine_id. |
| expiry_date | date | Ngày hết hạn. |
| status | varchar(15) | ACTIVE: được xét cấp; BLOCKED: khóa lô. |
| block_reason | text | Lý do khóa lô; bắt buộc khi BLOCKED. Còn lại cho phép NULL. |

Chỉ cấp lô ACTIVE còn hạn và có tồn AVAILABLE. Thuốc trả về giữ ở QUARANTINE cho tới khi được xử lý; không tự cộng vào tồn được phép cấp.

### 13 Nhà cung cấp

supplier — Danh mục đối tác để lập đề xuất nhập và ghi nguồn thuốc thực nhận.

| Tên trường | Kiểu dữ liệu | Mô tả ngắn |
| --- | --- | --- |
| id | bigint | Khóa chính tự tăng. |
| code | varchar(30) | Mã nhà cung cấp duy nhất. |
| name | varchar(200) | Tên nhà cung cấp. |
| phone | varchar(20) | Điện thoại liên hệ. Cho phép NULL. |
| address | text | Địa chỉ. Cho phép NULL. |
| is_active | boolean | Đang giao dịch; mặc định true. |

### 14 Phiếu nghiệp vụ kho

stock_document — Một cấu trúc phiếu dùng chung cho nhập, chuyển, cấp, trả, hủy thuốc và điều chỉnh sau kiểm kê.

| Tên trường | Kiểu dữ liệu | Mô tả ngắn |
| --- | --- | --- |
| id | bigint | Khóa chính tự tăng. |
| document_code | varchar(30) | Mã phiếu duy nhất. |
| type | varchar(20) | OPENING, RECEIPT, TRANSFER, DISPENSE, PATIENT_RETURN, SUPPLIER_RETURN, DISPOSAL hoặc ADJUSTMENT. |
| occurred_at | timestamptz | Thời điểm nghiệp vụ thực sự xảy ra; dùng tổng hợp lượng cấp theo ngày. |
| status | varchar(15) | DRAFT: nháp; POSTED: đã ghi sổ; CANCELLED: phiếu bị hủy. |
| stock_location | varchar(10) | MAIN: kho chính; COUNTER: điểm cấp phát ngoại trú. |
| stock_bucket | varchar(15) | AVAILABLE: tồn được xét cấp; QUARANTINE: tồn cách ly. |
| destination_location | varchar(10) | Điểm nhận của TRANSFER: MAIN hoặc COUNTER. Loại khác cho phép NULL. |
| destination_bucket | varchar(15) | Nhóm tồn nhận của TRANSFER. Loại khác cho phép NULL. |
| supplier_id | bigint | FK đến supplier. Bắt buộc với RECEIPT và SUPPLIER_RETURN; còn lại cho phép NULL. |
| prescription_id | bigint | FK đến prescription. Bắt buộc với DISPENSE và PATIENT_RETURN; còn lại cho phép NULL. |
| purchase_request_id | bigint | FK đến purchase_request. Đề xuất nhập liên quan; cho phép NULL. |
| posted_at | timestamptz | Thời điểm xác nhận ghi sổ. Cho phép NULL khi chưa ghi sổ. |
| posted_by | bigint | FK đến user_account. Nhân viên ghi sổ. Cho phép NULL khi chưa ghi sổ. |
| note | text | Nội dung hoặc lý do. Cho phép NULL; bắt buộc với trả, hủy thuốc, điều chỉnh hoặc hủy phiếu. |

Một phiếu dùng một điểm và một nhóm tồn nguồn; TRANSFER có thêm điểm và nhóm tồn đích. Có thể chuyển cùng điểm từ QUARANTINE sang AVAILABLE sau khi xử lý. Chỉ phiếu POSTED tác động tồn; đơn thuốc không trực tiếp trừ tồn.

### 15 Chi tiết phiếu kho

stock_document_item — Mỗi dòng ghi lượng của một lô. Đây là nguồn lượng thực cấp cho mô hình thuốc.

| Tên trường | Kiểu dữ liệu | Mô tả ngắn |
| --- | --- | --- |
| id | bigint | Khóa chính tự tăng. |
| document_id | bigint | FK đến stock_document. Phiếu chứa dòng. |
| batch_id | bigint | FK đến medicine_batch. Lô thực nhập hoặc xuất. |
| quantity | numeric(14,3) | Lượng theo đơn vị cơ sở. Thường lớn hơn 0; ADJUSTMENT cho phép số âm hoặc 0. |
| unit_cost | numeric(14,2) | Giá nhập trên một đơn vị cơ sở. Cho phép NULL nếu không áp dụng. |
| prescription_item_id | bigint | FK đến prescription_item. Bắt buộc khi cấp hoặc nhận trả từ bệnh nhân; còn lại cho phép NULL. |
| source_item_id | bigint | FK đến chính stock_document_item. Dòng cấp gốc khi bệnh nhân trả; còn lại cho phép NULL. |
| book_quantity | numeric(14,3) | Tồn sổ lúc kiểm kê; bắt buộc với ADJUSTMENT. Loại khác cho phép NULL. |
| counted_quantity | numeric(14,3) | Lượng kiểm đếm thực tế; bắt buộc với ADJUSTMENT. Loại khác cho phép NULL. |
| note | text | Ghi chú từng dòng hoặc tình trạng thuốc trả. Cho phép NULL. |

Một dòng đơn có thể được cấp từ nhiều lô và trong nhiều phiếu. Với ADJUSTMENT: quantity = counted_quantity − book_quantity. Thuốc của lô phải khớp thuốc trên dòng đơn; dòng đơn phải thuộc đơn của phiếu.

### 16 Đề xuất nhập thuốc

purchase_request — Dược sĩ lập, ADMIN duyệt một bước; lưu thêm ngày đặt và dự kiến nhận để theo dõi ở mức đơn giản.

| Tên trường | Kiểu dữ liệu | Mô tả ngắn |
| --- | --- | --- |
| id | bigint | Khóa chính tự tăng. |
| request_code | varchar(30) | Mã đề xuất duy nhất. |
| supplier_id | bigint | FK đến supplier. Nhà cung cấp dự kiến. Cho phép NULL khi chưa chọn. |
| status | varchar(15) | DRAFT, PENDING, APPROVED, REJECTED hoặc CANCELLED. |
| reviewed_by | bigint | FK đến user_account. ADMIN duyệt hoặc từ chối. Cho phép NULL. |
| reviewed_at | timestamptz | Thời điểm duyệt hoặc từ chối. Cho phép NULL. |
| ordered_at | timestamptz | Thời điểm đặt mua thực tế. Cho phép NULL khi chưa đặt. |
| expected_delivery_date | date | Ngày dự kiến nhận thuốc. Cho phép NULL. |
| closed_at | timestamptz | Thời điểm kết thúc theo dõi nhập hàng. Cho phép NULL. |
| note | text | Lý do đề xuất, từ chối hoặc kết thúc khi chưa nhận đủ. Cho phép NULL nếu không cần giải thích. |

Bản gọn duyệt toàn bộ số lượng đề xuất. Cần đổi lượng thì sửa trước duyệt hoặc lập đề xuất mới. Một đề xuất có thể nhận qua nhiều phiếu RECEIPT; lượng đã nhận tính từ các phiếu hợp lệ, không lưu lặp lại.

### 17 Chi tiết đề xuất nhập

purchase_request_item — Danh sách thuốc và lượng cần bổ sung; có thể dùng kết quả dự báo làm căn cứ khi tích hợp ML.

| Tên trường | Kiểu dữ liệu | Mô tả ngắn |
| --- | --- | --- |
| id | bigint | Khóa chính tự tăng. |
| request_id | bigint | FK đến purchase_request. Đề xuất chứa dòng. |
| medicine_id | bigint | FK đến medicine. Thuốc cần nhập. |
| quantity | numeric(14,3) | Số lượng đề xuất theo đơn vị cơ sở; lớn hơn 0. |
| note | text | Căn cứ chọn lượng nhập hoặc ghi chú. Cho phép NULL. |

Duy nhất theo request_id và medicine_id. Phiếu nhập gắn đề xuất chỉ nhận các thuốc có trong đề xuất đó; theo dõi tổng nhận theo thuốc.

### 18 Thông tin và cấu hình bệnh viện

hospital_config — Một dòng cấu hình chung cho một cơ sở.

| Tên trường | Kiểu dữ liệu | Mô tả ngắn |
| --- | --- | --- |
| id | bigint | Khóa chính tự tăng. |
| name | varchar(200) | Tên bệnh viện. |
| address | text | Địa chỉ bệnh viện. |
| phone | varchar(20) | Số điện thoại hoặc hotline. |
| email | varchar(150) | Email liên hệ. Cho phép NULL. |
| logo_url | text | Địa chỉ tệp logo. Cho phép NULL. |
| description | text | Giới thiệu ngắn về bệnh viện. Cho phép NULL. |
| advance_booking_days | integer | Số ngày tối đa được đặt trước; lớn hơn 0. |

Lịch giờ hoạt động chi tiết được thể hiện bằng clinic_slot. Phạm vi một cơ sở nên không cần hospital_id lặp lại trên mọi bảng.

## Quan hệ giữa các bảng

Ký hiệu 1—N: một bản ghi cha có thể được nhiều bản ghi con tham chiếu. Khóa ngoại được phép NULL nghĩa là bản ghi con có thể chưa liên kết cha. Quan hệ có “tối đa một” phải được đặt ràng buộc duy nhất tương ứng.

| Bảng cha | Bảng con và khóa ngoại | Quan hệ |
| --- | --- | --- |
| user_account | patient.user_id | 1—0..1. Một tài khoản liên kết tối đa một hồ sơ bệnh nhân. |
| user_account | doctor_profile.user_id | 1 — 0..1 |
| specialty | doctor_profile.specialty_id | 1 — N |
| specialty | clinic_room.specialty_id | 1—N. Liên kết bắt buộc. |
| clinic_room | clinic_slot.room_id | 1—N. Liên kết bắt buộc. |
| user_account | clinic_slot.doctor_id | 1—N. Liên kết bắt buộc. |
| patient | appointment.patient_id | 1—N. Liên kết bắt buộc. |
| clinic_slot | appointment.slot_id | 1—N. Liên kết bắt buộc. |
| user_account | appointment.confirmed_by | 1—N. Liên kết tùy chọn. |
| patient | medical_visit.patient_id | 1—N. Liên kết bắt buộc. |
| appointment | medical_visit.appointment_id | 1—0..1. Một lịch hẹn có tối đa một lượt; lượt trực tiếp không cần lịch hẹn. |
| clinic_slot | medical_visit.slot_id | 1—N. Liên kết bắt buộc. |
| medical_visit | prescription.visit_id | 1—N. Liên kết bắt buộc. |
| user_account | prescription.doctor_id | 1—N. Liên kết bắt buộc. |
| prescription | prescription_item.prescription_id | 1—N. Liên kết bắt buộc. |
| medicine | prescription_item.medicine_id | 1—N. Liên kết bắt buộc. |
| medicine | medicine_batch.medicine_id | 1—N. Liên kết bắt buộc. |
| supplier | stock_document.supplier_id | 1—N. Liên kết tùy chọn. |
| prescription | stock_document.prescription_id | 1—N. Liên kết tùy chọn. |
| purchase_request | stock_document.purchase_request_id | 1—N. Liên kết tùy chọn. |
| user_account | stock_document.posted_by | 1—N. Liên kết tùy chọn. |
| stock_document | stock_document_item.document_id | 1—N. Liên kết bắt buộc. |
| medicine_batch | stock_document_item.batch_id | 1—N. Liên kết bắt buộc. |
| prescription_item | stock_document_item.prescription_item_id | 1—N. Liên kết tùy chọn. |
| stock_document_item | stock_document_item.source_item_id | 1—N tự tham chiếu. Một dòng cấp có thể được trả nhiều lần. |
| supplier | purchase_request.supplier_id | 1—N. Liên kết tùy chọn. |
| user_account | purchase_request.reviewed_by | 1—N. Liên kết tùy chọn. |
| purchase_request | purchase_request_item.request_id | 1—N. Liên kết bắt buộc. |
| medicine | purchase_request_item.medicine_id | 1—N. Liên kết bắt buộc. |
