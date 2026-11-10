# M06.04 — Tồn đầu kỳ và nhập kho

Các API yêu cầu PHARMACIST đang hoạt động, POST cần CSRF.

- `POST /api/v1/pharmacy/stock-documents/openings`: tạo phiếu OPENING DRAFT, trả 201. Body `{"businessDate":"2026-11-10","stockLocation":"MAIN","stockBucket":"AVAILABLE","note":"<tùy chọn>","items":[{"batchId":1,"quantity":10.000}]}`. Ngày nghiệp vụ được lưu tại đầu ngày `Asia/Ho_Chi_Minh`; không nhận ngày tương lai.
- `POST /api/v1/pharmacy/stock-documents/receipts`: tạo phiếu RECEIPT DRAFT, trả 201. Body `{"occurredAt":"2026-11-10T10:00:00+07:00","stockLocation":"MAIN","stockBucket":"AVAILABLE","supplierId":1,"note":"<tùy chọn>","items":[{"batchId":1,"quantity":2.000,"unitCost":1.25}]}`. Nhà cung cấp phải đang hoạt động ở lúc tạo và lúc ghi sổ.
- `POST /api/v1/pharmacy/stock-documents/{id}/post`: ghi sổ; `GET /{id}`: xem phiếu; `POST /{id}/cancel`: hủy nháp theo M06.03.

Mọi lượng nhập theo `baseUnit`, dương và tối đa ba chữ số thập phân. Giá nhập tùy chọn, không âm, tối đa hai chữ số thập phân. Một lô chỉ có một dòng trong phiếu nhập/tồn đầu kỳ. DRAFT không tăng tồn; POSTED tăng đúng lô, vị trí và nhóm tồn. Gửi lặp lệnh ghi sổ trả lại phiếu POSTED cũ. Liên kết đề xuất nhập thuộc M07.04 và chỉ được mở sau khi có kiểm tra đề xuất đã duyệt và hạn mức nhận. Không có migration mới.
