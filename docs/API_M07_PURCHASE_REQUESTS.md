# M07 — Đề xuất nhập thuốc

Các API dưới `/api/v1/procurement/purchase-requests` yêu cầu phiên đăng nhập. PHARMACIST lập,
sửa/gửi/hủy bản nháp, ghi đặt hàng và kết thúc; ADMIN duyệt hoặc từ chối. Cả hai role được xem danh sách và chi tiết.
Mọi lệnh ghi cần CSRF. Bản nháp chỉ người lập được sửa/gửi/hủy. Thời điểm dùng ISO 8601 có offset;
lượng dùng đơn vị cơ sở, tối đa ba chữ số thập phân.

| API | Role | Kết quả |
| --- | --- | --- |
| `POST /` | PHARMACIST | 201, `Location`, tạo DRAFT |
| `PUT /{id}` | PHARMACIST, người lập | 200, thay toàn bộ nội dung DRAFT |
| `POST /{id}/submit` | PHARMACIST, người lập | 200, DRAFT → PENDING; gửi lại PENDING trả bản cũ |
| `POST /{id}/cancel` | PHARMACIST, người lập | 200, DRAFT → CANCELLED |
| `POST /{id}/approve` | ADMIN | 200, PENDING → APPROVED; gửi lại APPROVED trả bản cũ |
| `POST /{id}/reject` | ADMIN | 200, PENDING → REJECTED; cần lý do; gửi lại REJECTED trả bản cũ |
| `POST /{id}/order` | PHARMACIST | 200, ghi ngày đặt và dự kiến nhận cho APPROVED còn mở |
| `POST /{id}/close` | PHARMACIST | 200, kết thúc APPROVED; gửi lại trả bản cũ |
| `GET /`, `GET /{id}` | PHARMACIST, ADMIN | 200, danh sách phân trang và chi tiết |

Body tạo/sửa: `{"supplierId":1,"note":"Bổ sung tồn kho","items":[{"medicineId":1,"quantity":100.000,"note":null}]}`.
Nhà cung cấp và căn cứ có thể để trống ở DRAFT, nhưng cần có khi gửi; nhà cung cấp phải còn hoạt động khi gửi và duyệt. Thuốc phải còn hoạt động
khi thêm/sửa/gửi; mỗi thuốc một dòng, lượng dương. Duyệt luôn duyệt toàn bộ lượng và khóa nội dung.
Body từ chối/kết thúc: `{"reason":"Lý do nghiệp vụ"}`. Từ chối luôn cần lý do. Kết thúc khi còn thiếu
cần lý do và chỉ được làm sau khi xử lý hết phiếu RECEIPT nháp. Ghi đặt hàng:
`{"orderedAt":"2026-11-11T10:00:00+07:00","expectedDeliveryDate":"2026-11-12"}`.
Thời điểm đặt không trước khi duyệt hay sau hiện tại; ngày dự kiến không trước ngày đặt theo giờ bệnh viện.
Ghi đặt lặp cùng giá trị trả bản cũ; giá trị khác trả 409.

Chi tiết trả `reviewedBy`, `reviewedAt`, `reviewNote`, `orderedAt`, `expectedDeliveryDate`,
`closedAt`, `closeNote`, audit và từng dòng `quantity`, `receivedQuantity`, `remainingQuantity`.
Lượng đã nhận chỉ cộng các dòng phiếu RECEIPT POSTED liên kết, qua mọi lô và mọi đợt. Lịch sử nhận vẫn
đọc được sau khi đóng. Trạng thái sai trả 409 `PURCHASE_STATE_CONFLICT`; vượt lượng nhận trả 409
`PURCHASE_QUANTITY_EXCEEDED`; sai đối tác/thuốc trả 409 `PURCHASE_SUPPLIER_MISMATCH`/
`PURCHASE_MEDICINE_MISMATCH`; đề xuất chưa duyệt/đã đóng trả 409 `PURCHASE_NOT_RECEIVABLE`.

M06.04 nhận thêm `purchaseRequestId` tùy chọn trong body `POST /api/v1/pharmacy/stock-documents/receipts`.
Khi có liên kết, M07 khóa đề xuất và kiểm tra ở lúc tạo nháp lẫn lúc ghi sổ. Phiếu POSTED gửi lại vẫn trả
phiếu cũ. Phiếu DRAFT không được cộng vào lượng đã nhận; hai phiếu tranh cùng hạn mức được tuần tự hóa
trên bản ghi đề xuất trong transaction ghi sổ. Không có endpoint sửa phiếu POSTED.

Schema: migration `V4__purchase_request_decision_notes.sql` bổ sung `review_note`, `close_note`
để giữ riêng căn cứ lập, giải thích quyết định và giải thích kết thúc.
