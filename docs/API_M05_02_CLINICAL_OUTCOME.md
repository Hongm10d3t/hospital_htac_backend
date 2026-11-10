# M05.02 — Ghi nhận và kết thúc khám

Các API dưới `/api/v1/doctor/visits/{visitId}` chỉ dành cho bác sĩ đang hoạt động và được phân công khung của lượt. POST/PUT cần CSRF; lượt phải ở IN_PROGRESS khi ghi hoặc kết thúc lần đầu.

- `PUT /clinical`: thay toàn bộ ghi nhận chuyên môn của lượt. Body có `clinicalNotes` (tối đa 10000 ký tự), `diagnosisCode` (20), `diagnosis` (5000), `treatmentPlan` (10000), `followUpDate` (`YYYY-MM-DD`, tùy chọn, không ở quá khứ). Trường vắng mặt hoặc trắng được lưu NULL; ngày tái khám chỉ là khuyến nghị, không tạo lịch mới.
- `POST /complete`: yêu cầu `diagnosis` khác trắng đã được lưu; chuyển IN_PROGRESS → COMPLETED và ghi `completedAt`. Nếu có lịch hẹn, chốt lịch COMPLETED trong cùng giao dịch. Gửi lặp trả kết quả cũ và giữ thời điểm ban đầu.
- `POST /not-complete`: body `{"reason":"<lý do, tối đa 3000 ký tự>"}`. Chuyển IN_PROGRESS → NOT_COMPLETED; nếu có lịch hẹn, chốt lịch NOT_COMPLETED với cùng lý do trong cùng giao dịch. Gửi lặp cùng lý do trả kết quả cũ; lý do khác hoặc trạng thái cuối trái ngược bị từ chối.

Thiếu chẩn đoán trả 409 `DIAGNOSIS_REQUIRED`; trạng thái không hợp lệ trả 409 `VISIT_NOT_IN_PROGRESS`; lý do hoặc ngày tái khám sai trả 400. Giao dịch `READ_COMMITTED` khóa slot rồi lượt, M03 khóa lịch trong cùng giao dịch. Nếu chốt lịch thất bại, thay đổi trạng thái lượt được rollback. Không có migration mới.
