# M06.02 — Lô thuốc, hạn dùng và khóa lô

Các API `/api/v1/pharmacy/batches` yêu cầu PHARMACIST đang hoạt động; POST/PUT cần CSRF.

- `GET /api/v1/pharmacy/batches?medicineId=&status=&term=&page=&size=&sort=`: tra cứu lô; `GET /api/v1/pharmacy/batches/{id}`: chi tiết.
- `POST /api/v1/pharmacy/batches`: tạo lô, trả 201. `PUT /api/v1/pharmacy/batches/{id}`: cập nhật. Body `{"medicineId":1,"batchNumber":"<số lô>","expiryDate":"2027-12-31","status":"ACTIVE","blockReason":null}`. `BLOCKED` yêu cầu `blockReason` có nội dung.

Số lô duy nhất trong mỗi thuốc. Lô đã có dòng phiếu kho không thể đổi số lô, hạn dùng hay thuốc gốc; có thể khóa hoặc mở khóa có ghi nhận lý do khóa. Lô được xét cấp khi `status=ACTIVE`, thuốc còn hoạt động và `expiryDate` **sau** ngày hiện tại tại `Asia/Ho_Chi_Minh`. Lô hết hạn đúng ngày `expiryDate` từ đầu ngày đó. Điều kiện này được dùng lại ở bước ghi sổ DISPENSE; nhóm tồn QUARANTINE sẽ được loại tại bước kiểm tra số dư. Chưa có migration mới.
