# M06.06 — Cấp thuốc theo đơn và theo lô

Các API yêu cầu PHARMACIST đang hoạt động. POST cần CSRF.

- `GET /api/v1/pharmacy/prescriptions/{id}/dispensing`: đơn ISSUED và từng dòng với `prescribedQuantity`, `dispensedQuantity` (chỉ từ phiếu DISPENSE POSTED) và `remainingQuantity`.
- `POST /api/v1/pharmacy/stock-documents/dispensations`: tạo DISPENSE DRAFT, trả 201. Body `{"prescriptionId":1,"occurredAt":"2026-11-10T10:00:00+07:00","items":[{"prescriptionItemId":1,"batchId":2,"quantity":4.000},{"prescriptionItemId":1,"batchId":3,"quantity":2.000}]}`. Nguồn cấp cố định là COUNTER/AVAILABLE.
- `POST /api/v1/pharmacy/stock-documents/{id}/post`: kiểm tra lại và ghi sổ; `GET /{id}` xem chi tiết; `POST /{id}/cancel` hủy nháp.

Đơn phải ISSUED, dòng thuộc đúng đơn và lô thuộc đúng thuốc. Lô phải ACTIVE, thuốc đang hoạt động, `expiryDate` sau ngày nghiệp vụ tại `Asia/Ho_Chi_Minh`, và có tồn COUNTER/AVAILABLE. Một dòng đơn được cấp từ nhiều lô và nhiều đợt. Tổng lượng DISPENSE POSTED của dòng không vượt lượng kê; lượng trả sau này không tăng lại quyền cấp của đơn cũ. Cả tạo nháp và ghi sổ đều kiểm tra điều kiện, nhưng ghi sổ là quyết định cuối cùng dưới khóa đơn rồi khóa lô. Phiếu tạo trước khi lô bị khóa hoặc tồn thay đổi có thể bị từ chối khi ghi sổ; toàn phiếu rollback. Đơn DRAFT/CANCELLED không được cấp. Không có migration mới.
