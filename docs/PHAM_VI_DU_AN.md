# PHẠM VI DỰ ÁN QUẢN LÝ KHÁM NGOẠI TRÚ VÀ DƯỢC BỆNH VIỆN

## 1 Mục tiêu và phạm vi dự án

### 1 1 Mục tiêu

- Quản lý quy trình khám ngoại trú. Kết nối đặt lịch, tiếp đón, khám bệnh và kê đơn; ghi nhận đầy đủ lượt bệnh nhân thực sự đến, kể cả người đăng ký trực tiếp.

- Liên kết đơn thuốc với nghiệp vụ dược. Theo dõi lượng kê, lượng thực cấp, phần chưa nhận và tồn kho theo lô, hạn dùng để hỗ trợ cấp thuốc và bổ sung hàng.

- Hỗ trợ bệnh nhân sử dụng dịch vụ trực tuyến. Cho phép xem thông tin bác sĩ, đặt khám cho bản thân và tra cứu lịch sử khám, kết quả, đơn thuốc của mình.

- Tạo dữ liệu cho hai bài toán học máy. Sử dụng dữ liệu lượt khám và cấp thuốc để dự báo số lượt khám, dự báo lượng thuốc sử dụng, phục vụ tham khảo khi lập lịch và đề xuất nhập.

### 1 2 Phạm vi triển khai

- Một cơ sở khám bệnh. Quản lý khám ngoại trú tại một bệnh viện; mỗi phòng và mỗi bác sĩ thuộc một chuyên khoa trong phạm vi hiện tại.

- Hai hình thức tiếp nhận. Tiếp nhận bệnh nhân đặt lịch online và bệnh nhân đến trực tiếp. Hồ sơ bệnh nhân tồn tại độc lập với tài khoản đăng nhập.

- Năm vai trò cố định. ADMIN, RECEPTIONIST, DOCTOR, PHARMACIST và PATIENT. Chức vụ như trưởng khoa là thông tin hồ sơ, không tạo thêm quyền hệ thống.

- Kho dược ở mức đơn giản. Có kho chính và điểm cấp phát ngoại trú; tại mỗi nơi phân biệt tồn khả dụng và tồn cách ly. Mọi số lượng dùng đơn vị cơ sở của thuốc.

- Demo bằng dữ liệu import. Dữ liệu lịch sử được chuẩn bị và import để vận hành thử, huấn luyện và đánh giá mô hình. Kiểm tra độ đầy đủ thực hiện khi chuẩn bị dataset; không có bước xác nhận dữ liệu cuối ngày trên website.

- Tích hợp học máy theo giai đoạn. Hoàn thiện dữ liệu và nghiệp vụ trước, sau đó huấn luyện, chạy dự báo và hiển thị kết quả. Database hiện tại chưa có bảng lưu mô hình hoặc lịch sử dự báo.

### 1 3 Các giới hạn của phiên bản hiện tại

- Chưa triển khai điều trị nội trú, cấp cứu, xét nghiệm, chẩn đoán hình ảnh và các phân hệ chuyên môn độc lập. Chỉ số cơ bản và ghi nhận khám nằm trong nội dung lượt khám.

- Chưa triển khai viện phí, thanh toán, bảo hiểm y tế, kế toán, đấu thầu và quy trình mua sắm nhiều cấp. Đề xuất nhập thuốc được ADMIN duyệt một bước.

- Chưa triển khai quản lý nhiều cơ sở, đặt khám hộ, kiosk tiếp đón và quy đổi đơn vị đóng gói tự động. Người nhập tự quy đổi số lượng về đơn vị cơ sở.

- Xác nhận lịch hẹn thực hiện qua điện thoại và được nhân viên ghi nhận. OTP, SMS hoặc email tự động là phần mở rộng sau; số điện thoại tài khoản được chuẩn bị cho nhu cầu này.

- Dự báo hỗ trợ người dùng quyết định; chưa tự phân lịch bác sĩ, tự đặt mua thuốc hoặc tự huấn luyện lại theo lịch. Các chức năng đó không nằm trong phạm vi hiện tại.

## 2 Vai trò của hệ thống

Mỗi tài khoản có một vai trò. Bệnh nhân tự đăng ký chỉ nhận vai trò PATIENT; ADMIN tạo và quản lý tài khoản nhân viên. Quyền thao tác được kiểm tra tại backend theo vai trò và phạm vi dữ liệu liên quan.

| Vai trò | Mô tả và trách nhiệm chính | Phạm vi thao tác |
| --- | --- | --- |
| ADMIN<br>Quản trị viên | Quản lý tài khoản nhân viên, cấu hình bệnh viện, chuyên khoa, phòng khám và hồ sơ bác sĩ; lập lịch khám, duyệt đề xuất nhập, xem báo cáo và kết quả dự báo. | Thực hiện chức năng quản trị và phê duyệt. Không mặc định thay bác sĩ ghi kết quả khám hoặc thay dược sĩ cấp thuốc. |
| RECEPTIONIST<br>Nhân viên tiếp đón | Tra cứu và cập nhật hồ sơ định danh; xác nhận lịch hẹn bằng số điện thoại tài khoản; tiếp nhận người có hẹn và người đến trực tiếp, phân phòng và cấp số thứ tự. | Quản lý lịch hẹn, hồ sơ hành chính và lượt tiếp nhận. Không sửa chẩn đoán, đơn thuốc hoặc ghi sổ kho. |
| DOCTOR<br>Bác sĩ | Xem lịch được phân công và danh sách chờ; tra cứu thông tin khám liên quan; ghi nhận khám, kết luận, hướng xử trí, ngày khuyến nghị tái khám và kê đơn. | Thực hiện chuyên môn trên lượt được phân công và đơn do mình lập; đọc lịch sử bệnh nhân phục vụ khám. Không duyệt nhập hoặc điều chỉnh kho. |
| PHARMACIST<br>Dược sĩ | Quản lý danh mục thuốc, nhà cung cấp, lô và tồn kho; cấp, nhận trả thuốc; ghi nhận phần chưa nhận; lập đề xuất nhập và theo dõi nhận hàng. | Xem đơn và thông tin bệnh nhân cần cho cấp phát; ghi sổ nghiệp vụ dược và xem báo cáo thuốc. Không sửa lượng bác sĩ đã kê hoặc tự duyệt đề xuất nhập. |
| PATIENT<br>Bệnh nhân | Đăng ký và sử dụng tài khoản; xem thông tin bệnh viện, bác sĩ công khai; đặt và theo dõi lịch; xem lịch sử khám, kết quả và đơn thuốc cá nhân. | Chỉ truy cập hồ sơ và dữ liệu của mình. Không tự sửa kết quả khám, đơn thuốc hoặc thông tin nghiệp vụ kho. |

Người chưa đăng nhập có thể xem thông tin bệnh viện và hồ sơ bác sĩ đã công khai. Đây là quyền xem công khai, không phải một role mới trong database.

Số điện thoại bắt buộc nằm trong user_account và được dùng làm số liên hệ của người có tài khoản. patient không có trường phone; bệnh nhân đến trực tiếp chưa có tài khoản không cần cung cấp số điện thoại.

## 3 Danh sách module

Các module được chia theo nghiệp vụ. Một module có thể sử dụng dữ liệu của module khác; phần báo cáo và học máy khai thác các bảng đã có, không làm thay đổi cấu trúc database đã chốt.

| Mã | Module | Dữ liệu nền hoặc nguồn khai thác |
| --- | --- | --- |
| M01 | Quản trị tài khoản và cấu hình | user_account, hospital_config |
| M02 | Danh mục khám bệnh và hồ sơ bác sĩ | specialty, clinic_room, doctor_profile |
| M03 | Lịch khám và đặt hẹn | clinic_slot, appointment |
| M04 | Hồ sơ bệnh nhân và tiếp đón | patient, medical_visit; liên kết appointment |
| M05 | Khám ngoại trú và kê đơn | medical_visit, prescription, prescription_item |
| M06 | Quản lý dược và kho thuốc | medicine, medicine_batch, supplier, stock_document, stock_document_item |
| M07 | Đề xuất nhập thuốc | purchase_request, purchase_request_item; đối chiếu phiếu nhập |
| M08 | Báo cáo và thống kê | Tổng hợp từ dữ liệu khám, lịch hẹn, đơn thuốc, kho và đề xuất nhập |
| M09 | Dữ liệu demo và học máy | Dữ liệu lịch sử đã import; lượt khám thực tế và lượng thuốc thực cấp |

## 4 Chức năng của từng module

### M01 Quản trị tài khoản và cấu hình

Quản lý việc truy cập hệ thống và các thông tin dùng chung của một cơ sở khám bệnh.

Vai trò sử dụng: ADMIN quản trị; các tài khoản sử dụng chức năng cá nhân.

#### Tài khoản và quyền truy cập

- Đăng nhập và đăng xuất. Xác thực tài khoản đang hoạt động và mở các chức năng đúng vai trò được gán.

- Đăng ký tài khoản bệnh nhân. Tạo tài khoản PATIENT với số điện thoại bắt buộc; phối hợp M04 để liên kết hồ sơ đã có hoặc tạo hồ sơ mới.

- Quản lý tài khoản nhân viên. ADMIN tạo và cập nhật tài khoản tiếp đón, bác sĩ, dược sĩ; khi tạo bác sĩ, tạo kèm hồ sơ chuyên môn tương ứng.

- Khóa và mở tài khoản. Cho phép hoặc ngăn đăng nhập, đồng thời giữ các hồ sơ và lịch sử nghiệp vụ đã liên kết.

- Quản lý mật khẩu và thông tin cá nhân. Người dùng đổi mật khẩu, tên hiển thị và số điện thoại của mình. Khi quên mật khẩu, phiên bản demo cho phép ADMIN hỗ trợ đặt lại sau khi đối chiếu thông tin.

#### Thông tin và cấu hình bệnh viện

- Quản lý thông tin cơ sở. Cập nhật tên, địa chỉ, hotline, email, logo và nội dung giới thiệu để hiển thị cho người dùng.

- Cấu hình thời hạn đặt trước. Thiết lập số ngày tối đa bệnh nhân được đặt khám trước; lịch giờ khám cụ thể được quản lý tại M03.

### M02 Danh mục khám bệnh và hồ sơ bác sĩ

Quản lý chuyên khoa, phòng và hồ sơ bác sĩ phục vụ phân lịch, đặt khám và hiển thị thông tin cho bệnh nhân.

Vai trò sử dụng: ADMIN quản lý; PATIENT và người xem công khai tra cứu thông tin được công bố.

#### Chuyên khoa và phòng khám

- Quản lý chuyên khoa. Tạo, cập nhật mã, tên và trạng thái sử dụng; dùng chuyên khoa để lọc bác sĩ, phòng và tổng hợp báo cáo.

- Quản lý phòng khám. Khai báo mã, tên, vị trí, chuyên khoa và trạng thái hoạt động. Phòng đã có lịch sử được giữ nguyên chuyên khoa để bảo toàn thống kê.

#### Hồ sơ bác sĩ và thông tin công khai

- Quản lý hồ sơ chuyên môn. Gán một chuyên khoa cho bác sĩ; cập nhật ảnh, học hàm hoặc chức danh, chức vụ, năm bắt đầu hành nghề, giới thiệu, lĩnh vực chuyên sâu và chứng chỉ.

- Quản lý công khai hồ sơ. ADMIN hoàn thiện và bật hoặc tắt hiển thị hồ sơ bác sĩ; thông tin đăng nhập và số điện thoại cá nhân không thuộc hồ sơ công khai.

- Tra cứu bác sĩ. Bệnh nhân xem hồ sơ đã công khai, lọc theo chuyên khoa và xem lịch khám đang nhận đăng ký để chọn lịch phù hợp.

### M03 Lịch khám và đặt hẹn

Phân công bác sĩ theo ngày giờ, phòng và sức chứa; tiếp nhận lịch đặt online và theo dõi việc xác nhận, hoàn thành lịch hẹn.

Vai trò sử dụng: ADMIN lập lịch; PATIENT đặt và theo dõi lịch; RECEPTIONIST xác nhận và xử lý lịch hẹn.

#### Lập lịch khám

- Xem và tạo lịch theo tuần. ADMIN chọn tuần, ngày, bác sĩ, phòng và khoảng thời gian; hệ thống tạo các khung giờ cụ thể theo thời lượng và sức chứa đã nhập.

- Kiểm tra phân công. Ngăn các khung giao nhau của cùng bác sĩ hoặc cùng phòng; kiểm tra chuyên khoa phù hợp và trạng thái sử dụng của bác sĩ, phòng.

- Điều chỉnh và đóng khung khám. Cập nhật khung chưa phát sinh nghiệp vụ theo điều kiện cho phép; đóng nhận đăng ký mới khi cần. Giữ nguyên thông tin khung đã có lượt khám thực tế.

#### Đặt và xử lý lịch hẹn

- Tìm lịch còn nhận đăng ký. Bệnh nhân lọc theo ngày, chuyên khoa hoặc bác sĩ; xem phòng, giờ và số chỗ còn nhận trong thời hạn đặt trước.

- Đặt khám cho bản thân. Tạo lịch PENDING kèm lý do khám. Kiểm tra chỗ trong cùng giao dịch; lịch PENDING và CONFIRMED đều giữ suất để tránh đặt vượt sức chứa.

- Xác nhận lịch qua điện thoại. Tiếp đón liên hệ bằng số trong tài khoản, cập nhật CONFIRMED và lưu người, thời điểm xác nhận.

- Theo dõi và kết thúc lịch hẹn. Bệnh nhân xem trạng thái; tiếp đón hoặc quy trình khám cập nhật kết quả cuối cùng. Hẹn hoàn tất là COMPLETED; hủy hoặc không đến là NOT_COMPLETED kèm lý do.

Đóng một khung khám chỉ ngừng nhận thêm lịch, không tự xóa các lịch đã có. Lịch hẹn chưa được tiếp nhận không được tính là lượt khám thực tế; khi tiếp nhận, bệnh nhân tiếp tục dùng suất đã giữ.

### M04 Hồ sơ bệnh nhân và tiếp đón

Duy trì một hồ sơ định danh xuyên suốt các lần khám và ghi nhận thời điểm bệnh nhân thực sự được tiếp nhận.

Vai trò sử dụng: RECEPTIONIST quản lý hồ sơ và tiếp nhận; PATIENT xem hồ sơ cá nhân.

#### Hồ sơ và liên kết tài khoản

- Tra cứu và quản lý hồ sơ bệnh nhân. Tìm theo mã bệnh nhân, CCCD hoặc thông tin định danh; cập nhật họ tên, ngày sinh, giới tính và địa chỉ, ưu tiên sử dụng lại hồ sơ đã có.

- Tạo hồ sơ người đến trực tiếp. Cấp mã bệnh nhân và lưu hồ sơ dù chưa có tài khoản; CCCD có thể để trống. Nhóm bệnh nhân này không cần số điện thoại.

- Liên kết tài khoản với hồ sơ cũ. Khi đăng ký, đối chiếu CCCD: hồ sơ chưa gắn tài khoản được liên kết để giữ toàn bộ lịch sử; hồ sơ đã gắn tài khoản được hướng dẫn đăng nhập hoặc khôi phục quyền truy cập.

- Xử lý hồ sơ chưa có hoặc sai CCCD. Tiếp đón đối chiếu, bổ sung thông tin vào hồ sơ có sẵn trước khi liên kết. Không tạo thêm hồ sơ chỉ để thay thế một hồ sơ cũ đã xác định được.

- Tra cứu lịch sử cá nhân. Bệnh nhân xem các lần khám, kết quả và đơn thuốc thuộc hồ sơ đã liên kết, bao gồm lịch sử trước khi có tài khoản.

#### Tiếp nhận và danh sách chờ

- Tiếp nhận người có lịch hẹn. Kiểm tra bệnh nhân và lịch đã đặt, tạo một lượt khám gắn lịch hẹn khi họ thực sự đến; mỗi lịch hẹn có tối đa một lượt.

- Tiếp nhận người đến trực tiếp. Chọn hồ sơ, phòng, bác sĩ và khung phục vụ thực tế còn khả năng tiếp nhận; tạo lượt khám không cần lịch hẹn online.

- Cấp số và theo dõi chờ khám. Ghi thời điểm đến, cấp số thứ tự không trùng trong cùng phòng và ngày; chuyển danh sách WAITING cho bác sĩ phụ trách.

- Xử lý lượt không hoàn tất hoặc nhập nhầm. Ghi rõ lý do khi người đã đến bỏ dở hoặc khi vô hiệu bản ghi nhập nhầm. NOT_COMPLETED vẫn là lượt đến; VOIDED bị loại khỏi thống kê.

Tạo tài khoản và liên kết hoặc tạo hồ sơ phải thực hiện trong cùng giao dịch. Thông tin liên hệ của bệnh nhân có tài khoản được lấy từ user_account.phone; không lưu bản sao trong patient.

### M05 Khám ngoại trú và kê đơn

Ghi nhận nội dung khám và tạo đơn thuốc có thể chuyển sang cấp phát, bảo đảm các thông tin gắn đúng lượt khám và bác sĩ.

Vai trò sử dụng: DOCTOR thực hiện chuyên môn; PATIENT xem kết quả cá nhân; PHARMACIST đọc đơn phục vụ cấp thuốc.

#### Quy trình khám

- Xem lịch và danh sách bệnh nhân. Bác sĩ xem các khung được phân công, danh sách chờ và lịch sử khám liên quan của bệnh nhân đang tiếp nhận.

- Bắt đầu và ghi nhận khám. Chuyển lượt sang IN_PROGRESS, lưu thời điểm bắt đầu; ghi triệu chứng, tiền sử, dị ứng, chỉ số cơ bản và các ghi nhận chuyên môn.

- Hoàn tất hoặc kết thúc khám. Lưu chẩn đoán, mã chẩn đoán nếu sử dụng, hướng xử trí, lời dặn và thời điểm hoàn tất; trường hợp bỏ dở lưu trạng thái và lý do tương ứng.

- Khuyến nghị tái khám. Ghi ngày khuyến nghị cho bệnh nhân; ngày này không tự tạo lịch hẹn hoặc giữ một suất khám mới.

#### Đơn thuốc

- Soạn đơn nháp. Chọn thuốc trong danh mục, nhập lượng theo đơn vị cơ sở và hướng dẫn sử dụng. Mỗi thuốc xuất hiện tối đa một dòng trong một đơn.

- Xác nhận đơn thuốc. Bác sĩ chuyển đơn từ DRAFT sang ISSUED và lưu thời điểm kê; đơn đã xác nhận mới được dược sĩ cấp thuốc.

- Bổ sung hoặc hủy đơn theo điều kiện. Một lượt có thể có nhiều đơn bổ sung. Hủy cần lý do; phần đã cấp không được xóa hoặc sửa làm mất lịch sử sử dụng thuốc.

### M06 Quản lý dược và kho thuốc

Quản lý thuốc, lô, phiếu kho và cấp phát; liên kết số lượng thực cấp với từng dòng đơn để tính tồn và xây dựng dữ liệu học máy.

Vai trò sử dụng: PHARMACIST thao tác nghiệp vụ; ADMIN xem báo cáo quản trị.

#### Danh mục và theo dõi tồn

- Quản lý danh mục thuốc. Khai báo mã, tên, hoạt chất, hàm lượng, dạng bào chế, đơn vị cơ sở, tồn tối thiểu, thời gian chờ nhập và trạng thái sử dụng.

- Quản lý nhà cung cấp. Lưu mã, tên, thông tin liên hệ và trạng thái giao dịch phục vụ đề xuất nhập, nhập kho và trả thuốc.

- Quản lý lô và hạn dùng. Lưu số lô, hạn dùng; khóa lô khi có lý do. Cùng một mã thuốc không có hai lô trùng số lô.

- Tra cứu tồn và cảnh báo. Tính tồn theo thuốc, lô, địa điểm và nhóm tồn từ phiếu đã ghi sổ; theo dõi dưới ngưỡng và gần hết hạn. Tồn khả dụng loại lô khóa, hết hạn và tồn cách ly.

#### Phiếu nghiệp vụ kho

- Ghi tồn đầu kỳ. Lập phiếu OPENING theo lô và vị trí để khởi tạo số dư khi bắt đầu sử dụng hoặc chuẩn bị dữ liệu demo.

- Nhập thuốc. Lập RECEIPT theo nhà cung cấp và lô, lưu số lượng, giá nhập nếu có; có thể liên kết đề xuất nhập đã duyệt.

- Chuyển thuốc. Lập TRANSFER giữa kho chính và quầy hoặc giữa nhóm cách ly và khả dụng sau khi xử lý; ghi rõ nguồn và đích.

- Trả nhà cung cấp và hủy thuốc. Lập SUPPLIER_RETURN hoặc DISPOSAL theo lô, số lượng và lý do; trả nhà cung cấp phải ghi rõ đối tác nhận trả.

- Kiểm kê và điều chỉnh. Lưu tồn sổ, lượng kiểm đếm và chênh lệch bằng ADJUSTMENT; có giải thích và chỉ tác động tồn sau khi ghi sổ.

- Quản lý trạng thái phiếu. Soạn DRAFT, kiểm tra và chuyển POSTED kèm người, thời điểm ghi sổ. Khóa nội dung đã ghi sổ; việc hủy hoặc sửa sai phải có lý do và bảo toàn lịch sử, số dư liên quan.

#### Cấp phát và thuốc chưa nhận

- Tiếp nhận đơn hợp lệ. Tra cứu đơn ISSUED, kiểm tra thuốc được kê, lượng đã cấp và lượng còn có thể cấp cho từng dòng.

- Cấp thuốc theo lô. Lập DISPENSE từ lô ACTIVE còn hạn và có tồn khả dụng; một dòng đơn có thể cấp qua nhiều lô hoặc nhiều lần, tổng cấp không vượt lượng kê và không làm tồn âm.

- Ghi nhận phần chưa nhận. Cập nhật lượng chưa nhận tại lần xử lý gần nhất, lý do hết thuốc, từ chối, mua ngoài hoặc lý do khác; lưu thời điểm dược sĩ đã kiểm tra việc nhận thuốc.

- Nhận thuốc bệnh nhân trả. Lập PATIENT_RETURN gắn dòng cấp gốc và dòng đơn, kiểm tra lượng trả; thuốc được đưa vào tồn cách ly để chờ xử lý, không tự trở thành tồn khả dụng.

Đơn thuốc không trực tiếp trừ tồn; chỉ phiếu POSTED tác động tồn. Lượng chưa nhận là hiện trạng gần nhất, không cộng dồn các lần cập nhật và không suy ra đã cấp đủ khi đơn chưa được dược sĩ kiểm tra.

### M07 Đề xuất nhập thuốc

Theo dõi nhu cầu bổ sung thuốc từ lúc dược sĩ lập đề xuất đến khi ADMIN duyệt, đặt hàng và nhận thực tế.

Vai trò sử dụng: PHARMACIST lập và theo dõi; ADMIN phê duyệt.

#### Lập và phê duyệt đề xuất

- Lập đề xuất nhập. Chọn nhà cung cấp dự kiến, thuốc, số lượng theo đơn vị cơ sở và căn cứ đề xuất; mỗi thuốc có một dòng trong đề xuất.

- Gửi và xử lý phê duyệt. Dược sĩ chuyển DRAFT sang PENDING. ADMIN duyệt toàn bộ lượng đề xuất hoặc từ chối, lưu người, thời điểm và nội dung giải thích khi cần.

- Điều chỉnh hoặc hủy theo điều kiện. Sửa lượng trước khi duyệt hoặc lập đề xuất mới khi nhu cầu thay đổi; đề xuất đã duyệt giữ nội dung để đối chiếu việc nhận hàng.

#### Đặt và nhận hàng

- Theo dõi đặt mua. Ghi thời điểm đặt thực tế, ngày dự kiến nhận và tình trạng còn theo dõi; không mở rộng thành quy trình hợp đồng, hóa đơn hoặc thanh toán.

- Đối chiếu nhận qua nhiều đợt. Liên kết các phiếu RECEIPT hợp lệ với đề xuất đã duyệt, đúng nhà cung cấp và thuốc; tính lượng đã nhận, còn thiếu theo thuốc, không vượt lượng duyệt.

- Kết thúc theo dõi. Ghi thời điểm kết thúc; giải thích nếu dừng nhận khi chưa đủ lượng. Kết quả dự báo có thể được dược sĩ sử dụng làm căn cứ lập đề xuất.

### M08 Báo cáo và thống kê

Cung cấp số liệu tổng hợp từ dữ liệu đã ghi nhận để theo dõi vận hành và kiểm tra đầu vào cho các mô hình.

Vai trò sử dụng: ADMIN xem tổng hợp; RECEPTIONIST xem hẹn và tiếp nhận; DOCTOR xem công việc được phân công; PHARMACIST xem nghiệp vụ dược.

#### Báo cáo khám bệnh

- Thống kê lịch hẹn. Tổng hợp số hẹn theo thời gian, trạng thái, bác sĩ hoặc phòng; phân biệt hẹn đăng ký với bệnh nhân đã thực sự đến.

- Thống kê lượt đến và kết quả xử lý. Đếm theo ngày tiếp nhận thực tế, chuyên khoa, phòng hoặc bác sĩ; loại VOIDED và vẫn tính người đã đến nhưng không hoàn tất khám.

#### Báo cáo dược và nhập thuốc

- Đối chiếu kê và cấp. Xem lượng kê, thực cấp theo phiếu và hiện trạng chưa nhận theo lý do; không cộng dồn hiện trạng thiếu thuốc thành lịch sử phát sinh.

- Báo cáo tồn và biến động kho. Tra cứu nhập, xuất, chuyển, trả, hủy và điều chỉnh theo thuốc, lô, thời gian, địa điểm; phân biệt tồn khả dụng với tồn cách ly.

- Theo dõi đề xuất và nhận hàng. Tổng hợp đề xuất chờ duyệt, đã duyệt, ngày dự kiến nhận và lượng còn thiếu; số lượng thực nhận lấy từ phiếu nhập hợp lệ.

### M09 Dữ liệu demo và học máy

Chuẩn bị dữ liệu lịch sử và triển khai hai bài toán dự báo phục vụ demo, sử dụng đúng các sự kiện nghiệp vụ có trong database.

Vai trò sử dụng: ADMIN quản lý việc chuẩn bị và chạy demo; ADMIN xem dự báo lượt khám, ADMIN và PHARMACIST xem dự báo thuốc.

#### Chuẩn bị dữ liệu

- Import dữ liệu demo. Chuẩn bị và nạp dữ liệu theo thứ tự phụ thuộc, giữ đúng liên kết hồ sơ, lượt khám, đơn, lô và phiếu kho. Đây có thể là script hoặc quy trình kỹ thuật, không bắt buộc một màn hình import riêng.

- Kiểm tra và tổng hợp dataset. Kiểm tra trùng, thiếu, thời gian và số lượng; xác định khoảng dữ liệu đầy đủ, phân biệt ngày không phát sinh với ngày bị thiếu trước khi tạo chuỗi theo ngày.

- Tạo dữ liệu huấn luyện. Tổng hợp lượt khám và lượng thực cấp theo định nghĩa ở bảng dưới; dùng ngày nghiệp vụ lịch sử, không lấy ngày import làm ngày phát sinh.

#### Huấn luyện và sử dụng dự báo

- Huấn luyện và đánh giá mô hình. Thực hiện ở thành phần xử lý ML, chia dữ liệu theo thời gian và đánh giá trên giai đoạn chưa dùng để học; thuật toán được lựa chọn trong bước nghiên cứu.

- Dự báo số lượt khám. Ước lượng lượt đến theo ngày cho một đến hai tuần tiếp theo; hiển thị để ADMIN tham khảo khi chuẩn bị phòng và lịch bác sĩ.

- Dự báo lượng thuốc sử dụng. Ước lượng lượng thực cấp theo từng mã thuốc trong kỳ dự báo; đối chiếu tồn khả dụng và thời gian chờ nhập để dược sĩ tham khảo khi lập đề xuất.

- Hiển thị và trao đổi kết quả. Đưa kết quả từ thành phần ML về giao diện demo hoặc xuất tệp để kiểm tra. Kết quả là thông tin hỗ trợ; quyết định phân lịch và nhập thuốc vẫn do người dùng thực hiện.

| Bài toán | Nguồn và cách tổng hợp | Đầu ra phục vụ demo |
| --- | --- | --- |
| Dự báo lượt khám | medical_visit theo ngày của arrived_at; loại VOIDED. Có thể phân nhóm chuyên khoa qua phòng của khung khám. Mỗi lượt được đếm một lần. | Ngày dự báo và số lượt ước lượng; có thể đối chiếu số thực tế khi giai đoạn đó đã phát sinh dữ liệu. |
| Dự báo thuốc | stock_document_item của phiếu DISPENSE có trạng thái POSTED, theo ngày occurred_at và mã thuốc qua medicine_batch. Không gộp chuyển kho, trả hoặc hủy thuốc vào lượng thực cấp. | Mã thuốc, ngày hoặc kỳ dự báo, số lượng ước lượng theo đơn vị cơ sở; dùng tham khảo bổ sung tồn. |

Đối với bài toán thuốc, lượng thực cấp là đại lượng dự báo cơ sở. Khi có thiếu thuốc, số này có thể thấp hơn nhu cầu; phần chưa nhận được dùng để phân tích bổ sung, nhưng database hiện chỉ giữ hiện trạng gần nhất nên chưa tạo được chuỗi lịch sử đầy đủ của nhu cầu chưa được đáp ứng.

Phiên bản 18 bảng chưa lưu mô hình, lần chạy hoặc lịch sử kết quả dự báo. Phạm vi demo sử dụng tệp mô hình và kết quả trả về từ thành phần ML; việc bổ sung lưu trữ lâu dài hoặc tự chạy định kỳ được xác định ở giai đoạn mở rộng.
