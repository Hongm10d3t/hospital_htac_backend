# M06.09 — Trả nhà cung cấp, hủy thuốc và kiểm kê

Các API yêu cầu PHARMACIST đang hoạt động, POST cần CSRF, trả phiếu DRAFT (201). Ghi sổ qua `POST /api/v1/pharmacy/stock-documents/{id}/post`.

- `POST /api/v1/pharmacy/stock-documents/supplier-returns`: body `{"occurredAt":"2026-11-10T10:00:00+07:00","stockLocation":"MAIN","stockBucket":"QUARANTINE","supplierId":1,"reason":"<lý do>","items":[{"batchId":2,"quantity":2.000}]}`. Nhà cung cấp và lý do bắt buộc; nguồn phải đủ tồn.
- `POST /api/v1/pharmacy/stock-documents/disposals`: cùng cấu trúc nhưng không có `supplierId`; lý do bắt buộc, trừ lượng từ nguồn.
- `POST /api/v1/pharmacy/stock-documents/adjustments`: body `{"occurredAt":"2026-11-10T10:00:00+07:00","stockLocation":"MAIN","stockBucket":"AVAILABLE","reason":"<lý do kiểm kê>","items":[{"batchId":2,"bookQuantity":10.000,"countedQuantity":8.000,"note":null}]}`. Backend tính và lưu `quantity = countedQuantity - bookQuantity`; chênh lệch dương, âm hoặc 0 đều hợp lệ.

Phiếu kiểm kê giữ tồn sổ lúc lập. Lúc tạo và ghi sổ, tồn hiện tại phải bằng `bookQuantity`; nếu đã có biến động xen giữa, trả 409 `STOCK_COUNT_STALE` để đọc lại tồn và kiểm đếm lại. Ghi sổ trừ/cộng chênh lệch một lần; số dư không âm. Phiếu POSTED bất biến. Nếu sửa sai, tạo phiếu nghiệp vụ mới có lý do và ghi mã phiếu liên quan trong ghi chú; không thay đổi dòng lịch sử. Không có migration mới.
