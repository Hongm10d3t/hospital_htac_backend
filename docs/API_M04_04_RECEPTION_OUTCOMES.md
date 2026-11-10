# M04.04 — Số thứ tự và kết quả tiếp đón

Số thứ tự bắt đầu từ 1 theo từng `(roomId, queueDate)` và tăng theo lần tiếp nhận. Ngày tính theo `Asia/Ho_Chi_Minh`; số của lượt VOIDED vẫn được giữ, không cấp lại. Giao dịch `READ_COMMITTED` khóa `clinic_slot` để giữ sức chứa chung, rồi khóa `clinic_room` trước khi đọc số lớn nhất và tạo lượt. MySQL có `UNIQUE(room_id, queue_date, queue_number)` bảo vệ cuối cùng. Phòng khác hoặc ngày khác có dãy số riêng.

Hai API dưới đây chỉ dành cho RECEPTIONIST đang hoạt động, cần CSRF token. Body đều là `{"reason":"<lý do, tối đa 3000 ký tự>"}`; lý do trắng bị từ chối.

- `POST /api/v1/reception/visits/{visitId}/not-complete`: WAITING → NOT_COMPLETED khi bệnh nhân bỏ dở. Lượt vẫn được tính là đã đến. Nếu có lịch hẹn, cập nhật lịch sang NOT_COMPLETED trong cùng giao dịch. Gửi lặp cùng lý do trả kết quả cũ.
- `POST /api/v1/reception/visits/{visitId}/void`: WAITING → VOIDED khi bản ghi tiếp nhận sai. Không cho vô hiệu nếu đã có ghi nhận chuyên môn hoặc bất kỳ đơn thuốc nào; phiếu kho luôn liên kết qua đơn nên cũng được bảo vệ. VOIDED không tính là lượt thực tế. Nếu có lịch hẹn, lịch được chốt NOT_COMPLETED cùng lý do và suất giữ được giải phóng. Gửi lặp cùng lý do trả kết quả cũ.

Lượt IN_PROGRESS/COMPLETED và kết quả cuối với lý do khác trả 409 `VISIT_NOT_WAITING`; lượt có dữ liệu chuyên môn hoặc đơn trả 409 `VISIT_HAS_CLINICAL_DATA`. Tiếp đón không có API ghi chẩn đoán, kết luận hoặc đơn thuốc. Lượt không tồn tại trả 404.
