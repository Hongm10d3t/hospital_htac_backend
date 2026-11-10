# M04.05 — Lịch sử khám cá nhân

Các API dưới `/api/v1/patients/me` chỉ dành cho tài khoản PATIENT đang hoạt động và đã liên kết hồ sơ. Patient ID luôn lấy từ phiên đăng nhập qua `patient.user_id`; client không chọn bệnh nhân khác. Mọi phản hồi có `Cache-Control: no-store`. ID lượt hoặc đơn của người khác trả 404 như ID không tồn tại.

- `GET /visits?from=YYYY-MM-DD&to=YYYY-MM-DD&page=0&size=20&sort=arrivedAt,DESC`: danh sách lượt của hồ sơ, gồm cả lượt trước khi có tài khoản. Ngày đầu/cuối tính bao gồm theo `queueDate` tại bệnh viện. Sort hỗ trợ `arrivedAt`, `id`.
- `GET /visits/{visitId}`: chi tiết lượt, kết quả và danh sách đơn đã công bố của lượt đó.
- `GET /prescriptions?from=YYYY-MM-DD&to=YYYY-MM-DD&page=0&size=20&sort=issuedAt,DESC`: danh sách đơn đã công bố thuộc các lượt của hồ sơ. Sort hỗ trợ `issuedAt`, `id`.
- `GET /prescriptions/{prescriptionId}`: chi tiết đơn và các dòng gồm thuốc, đơn vị cơ sở, lượng kê, hướng dẫn sử dụng.

Lượt `VOIDED` bị ẩn. Lượt WAITING, IN_PROGRESS, NOT_COMPLETED và COMPLETED xuất hiện cùng trạng thái; chẩn đoán, ghi nhận chuyên môn và hướng xử trí chỉ hiện khi COMPLETED. Đơn DRAFT bị ẩn; đơn ISSUED và đơn CANCELLED từng được phát hành (có `issuedAt`) được hiển thị để người bệnh biết đơn đã hủy. Lọc ngày áp dụng theo ngày tiếp nhận của lượt, kể cả đối với danh sách đơn.

`from > to`, tham số trang/sort sai trả 400. Tài khoản chưa liên kết hồ sơ trả 409 `PATIENT_PROFILE_REQUIRED`; không có dữ liệu của người khác trong danh sách hay chi tiết. Nội dung chẩn đoán và đơn chỉ đọc từ module M05; tên/đơn vị thuốc lấy từ module M06. Không có migration mới.
