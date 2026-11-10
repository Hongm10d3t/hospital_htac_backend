# M06.07 — Phần thuốc chưa nhận

Các API yêu cầu PHARMACIST đang hoạt động. POST cần CSRF.

- `GET /api/v1/pharmacy/prescriptions/{id}/dispensing`: trả `dispensingCheckedAt`, `reviewed`, lượng kê, lượng DISPENSE POSTED, lượng còn có thể cấp và `unfilledQuantity`/`unfilledReason`/`unfilledNote` của từng dòng. `reviewed=false` khi chưa có lần kiểm tra, kể cả `unfilledQuantity=0`.
- `POST /api/v1/pharmacy/prescriptions/{id}/dispensing-review`: thay trạng thái phần chưa nhận của **tất cả** dòng đơn ISSUED, trả snapshot mới. Body `{"items":[{"prescriptionItemId":1,"unfilledQuantity":4.000,"reason":"STOCKOUT","note":null}]}`.

Lượng chưa nhận là hiện trạng tại lần kiểm tra mới nhất, không cộng dồn giữa các lần cập nhật. Mỗi dòng chỉ xuất hiện một lần; lượng từ 0 đến `prescribedQuantity - dispensedQuantity` tại lúc giữ khóa đơn. Khi lượng dương, lý do phải là STOCKOUT, DECLINED, EXTERNAL hoặc OTHER; OTHER cần ghi chú. Khi lượng 0, lý do và ghi chú phải trống. `dispensingCheckedAt` chỉ được ghi sau lần kiểm tra thành công; nếu DISPENSE POSTED tiếp sau một lần kiểm tra, phần chưa nhận trước đó được giảm về tối đa lượng còn có thể cấp và thời điểm kiểm tra được cập nhật. Đơn chưa từng kiểm tra vẫn giữ `reviewed=false` sau lần cấp đầu cho đến khi có đánh giá phần chưa nhận. Không có migration mới.
