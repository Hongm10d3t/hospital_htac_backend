# M06.03 — Phiếu kho và ghi sổ

Các API `/api/v1/pharmacy/stock-documents` yêu cầu PHARMACIST đang hoạt động; POST cần CSRF.

- `GET /{id}`: chi tiết phiếu và dòng phiếu.
- `POST /{id}/post`: ghi sổ một phiếu DRAFT. Nếu đã POSTED, trả lại cùng phiếu và cùng `postedAt`; nếu CANCELLED, trả 409.
- `POST /{id}/cancel`: hủy phiếu DRAFT với body `{"reason":"<lý do>"}`. Phiếu POSTED không thể hủy hoặc sửa.

Phiếu DRAFT và CANCELLED không ảnh hưởng số dư. Phiếu POSTED là lịch sử bất biến; sai sót sau ghi sổ được xử lý bằng phiếu nghiệp vụ mới, ghi tham chiếu mã phiếu gốc trong ghi chú. Mỗi giao dịch ghi sổ khóa phiếu và các lô liên quan theo thứ tự ID, tính số dư từ lịch sử POSTED, kiểm tra mọi vị trí trước khi ghi trạng thái, người và thời điểm. Lỗi một dòng rollback toàn bộ. Các loại phiếu được mở từng gói M06.04–M06.09 sau khi có kiểm tra nghiệp vụ tương ứng.

| Loại | Vị trí nguồn | Vị trí đích |
| --- | --- | --- |
| OPENING, RECEIPT | Cộng | — |
| TRANSFER | Trừ | Cộng cùng lượng |
| DISPENSE, SUPPLIER_RETURN, DISPOSAL | Trừ | — |
| PATIENT_RETURN | Cộng QUARANTINE | — |
| ADJUSTMENT | Cộng quantity có dấu | — |

Mọi vị trí được xác định bởi `(batchId, stockLocation, stockBucket)`; vị trí MAIN/COUNTER và nhóm AVAILABLE/QUARANTINE. Không có bảng cache số dư hay migration mới. Gói này mở OPENING, RECEIPT, TRANSFER trong engine; luồng tạo phiếu và các kiểm tra riêng của chúng thuộc M06.04–05.
