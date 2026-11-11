# M08 — Báo cáo và thống kê

Các API chỉ đọc dưới `/api/v1/reports`. Response danh sách dùng `items`, `page`, `size`,
`totalItems`, `totalPages`; `page` bắt đầu từ 0, `size` từ 1 đến 100. Sort theo
`field,ASC|DESC` với trường cho phép ghi bên dưới. Ngày lọc là `[from, to)` theo
`Asia/Ho_Chi_Minh`, được đổi thành ranh giới UTC khi truy vấn. Thiếu hoặc đảo khoảng
ngày trả 400. Các API dùng dữ liệu đã lưu, không ghi thêm bảng hay phiếu.

## M08.01 — Hẹn và lượt đến

ADMIN và RECEPTIONIST xem toàn viện. DOCTOR chỉ xem slot mình được phân công; nếu
truyền `doctorId` khác tài khoản hiện tại, trả 403. Hai API nhận `from`, `to`,
`doctorId`, `roomId`, `status`, `page`, `size`, `sort`:

- `GET /appointments`: mỗi dòng là `(date,status,doctorId,roomId,count)`. Ngày lấy từ
  `clinic_slot.start_at`, kể cả hẹn chưa đến. Sort `date` hoặc `count`.
- `GET /visits`: thêm bộ lọc/cột `specialtyId`. Ngày lấy từ `medical_visit.arrived_at`;
  NOT_COMPLETED vẫn tính, VOIDED luôn loại. Mỗi lượt được đếm một lần, kể cả khi có
  nhiều đơn thuốc. Sort `date` hoặc `count`.

Ví dụ `GET /api/v1/reports/visits?from=2026-11-11&to=2026-11-12&roomId=1`
trả các nhóm lượt thực sự đến trong ngày 11/11 theo giờ bệnh viện. Response có thêm
`from`, `to`, `timeBasis` để giải thích ngày của báo cáo.

## M08.02 — Kê, cấp và chưa nhận

ADMIN và PHARMACIST được xem. `GET /prescription-medicines?from=&to=&medicineId=&page=&size=&sort=`
trả theo thuốc: `prescribedQuantity` từ dòng đơn ISSUED có `issued_at` trong kỳ;
`dispensedQuantity` từ dòng DISPENSE POSTED có `occurred_at` trong kỳ;
`currentUnfilledQuantity` từ **hiện trạng** dòng đơn phát hành trong kỳ có
`dispensing_checked_at`, không cộng các lần cập nhật; và `uncheckedLineCount` cho
dòng thuộc đơn chưa được kiểm tra. Vì hai lượng dùng hai thời điểm khác nhau, cấp
trong kỳ có thể thuộc đơn phát hành kỳ trước. Response nhắc lại `from`, `to`,
`filters` và `timeBasis`. DRAFT, CANCELLED, phiếu nháp, phiếu
trả và chuyển kho không cộng vào thực cấp. Sort `medicineId`,
`prescribedQuantity`, `dispensedQuantity`.

- `GET /prescriptions/{id}`: đơn ISSUED, thời điểm phát hành, lần kiểm tra gần nhất,
  cờ `reviewed`, từng dòng kê, tổng DISPENSE POSTED toàn thời gian, hiện trạng chưa
  nhận và lý do. `reviewed=false` giữ nghĩa **chưa kiểm tra** dù lượng chưa nhận bằng 0.
- `GET /prescriptions/{id}/dispense-lines?page=&size=&sort=`: dòng cấp POSTED theo
  phiếu, lô, thời điểm và lượng; sort `occurredAt`, `documentId`, `itemId`.

## M08.03 — Kho và đề xuất nhập

ADMIN và PHARMACIST được xem.

- `GET /stock-balances?medicineId=&batchId=&location=&bucket=&page=&size=&sort=`:
  số dư hiện tại theo thuốc/lô/điểm/nhóm. `physicalQuantity` cộng mọi phiếu POSTED
  theo quy tắc M06; `dispensableQuantity` chỉ ở AVAILABLE của thuốc/lô hoạt động,
  lô còn hạn theo ngày bệnh viện. Sort `medicineId`, `batchId`, `physicalQuantity`.
- `GET /stock-movements?from=&to=&medicineId=&batchId=&location=&bucket=&type=&page=&size=&sort=`:
  nhóm theo thuốc/lô/điểm/nhóm/loại phiếu, trả `inQuantity`, `outQuantity`,
  `netQuantity`, `documentCount`. Chỉ POSTED, lọc theo `occurred_at`. Chuyển nội bộ
  tạo một dòng trừ nguồn và một dòng cộng đích, nên tổng toàn viện bằng 0. Sort
  `medicineId`, `batchId`, `type`, `netQuantity`.
- `GET /stock-movement-lines` nhận cùng bộ lọc, trả biến động có dấu kèm
  `documentId`, `documentCode`, `itemId` để truy ngược chi tiết phiếu. Sort
  `occurredAt`, `documentId`, `itemId`.
- `GET /purchase-requests?status=&supplierId=&expectedFrom=&expectedTo=&page=&size=&sort=`:
  mỗi dòng đề xuất/thuốc có trạng thái, ngày đặt, ngày dự kiến, lượng yêu cầu, lượng
  duyệt, đã nhận và còn thiếu. `expectedFrom` gồm đầu, `expectedTo` loại cuối;
  chỉ các phiếu RECEIPT POSTED liên kết được tính đã nhận, cộng mọi lô và đợt.
  Lượng duyệt/còn thiếu bằng 0 cho đề xuất chưa APPROVED. Sort `requestId`,
  `expectedDeliveryDate`, `status`.

Role sai trả 403, không đăng nhập trả 401, ID đơn không tồn tại hoặc chưa phát hành
trả 404. Bộ lọc/nhóm/sort không hợp lệ hoặc trang vượt phạm vi trả 400. Dữ liệu
trả về không chứa thông tin định danh bệnh nhân, nội dung khám hay entity JPA.
M08 không cần migration mới.
