# M03.02 — Điều chỉnh, đóng khung và sức chứa

ADMIN dùng session và CSRF. Các API đều nằm dưới `/api/v1/admin/clinic-slots`:

- `GET /{id}`: chi tiết khung, kể cả CLOSED.
- `GET` với `roomId`, `doctorId`, `from`, `to`, `page`, `size`, `sort`: xem lịch phân trang. `from` gồm đầu, `to` loại cuối trên `startAt`; thời điểm có offset. Sắp xếp `id|startAt|endAt|capacity,asc|desc`.
- `PUT /{id}` với `{"startAt":"2030-01-01T08:00:00+07:00","endAt":"2030-01-01T08:15:00+07:00","capacity":3}`: đổi thời gian/sức chứa, trả 200. Bác sĩ và phòng của khung không đổi; muốn phân công khác thì tạo khung mới khi lịch cũ không còn phù hợp. Khung phải ở tương lai, không giao lịch khác. Đã có bất kỳ `medical_visit` nào, kể cả VOIDED, trả 409 `SLOT_HAS_VISITS`; đã có lịch hẹn thì chỉ cho tăng sức chứa, giữ nguyên thời gian, nếu sai trả `SLOT_HAS_APPOINTMENTS`.
- `POST /{id}/close`: trả 204, gọi lặp giữ CLOSED. Không xóa hoặc đổi trạng thái lịch hẹn hiện có. Không có API mở lại; tạo khung mới nếu cần nhận đăng ký tiếp.

Sức chứa là một hợp đồng dùng chung của M03 và luồng tiếp đón M04/M05. Với từng slot, `occupied = số medical_visit khác VOIDED + số appointment PENDING/CONFIRMED chưa có medical_visit khác VOIDED`. Lượt NOT_COMPLETED vẫn đếm vì đã đến. Hẹn NOT_COMPLETED không có lượt không giữ suất; hẹn đã chuyển thành lượt chỉ tính một lần. Nếu lượt VOIDED gắn hẹn còn PENDING/CONFIRMED, hẹn tiếp tục giữ suất cho đến khi trạng thái hẹn được xử lý. `available = max(0, capacity - occupied)`.

`SlotCapacityService.requireOpenSlotWithSpace` là hợp đồng đặt hẹn: khóa slot và kiểm tra OPEN/chỗ còn. `requireSpaceForReception(slotId, appointmentId)` là hợp đồng M04/M05: lượt trực tiếp cần OPEN và chỗ còn; hẹn đang giữ suất được tiếp nhận bằng suất cũ kể cả slot đã CLOSED; hẹn đã chuyển thành lượt hoặc không còn giữ suất bị từ chối. Các bên ghi `appointment` hoặc `medical_visit` phải gọi hợp đồng này trong **cùng giao dịch READ_COMMITTED** trước khi ghi. Ở MySQL REPEATABLE_READ, truy vấn trước khi chờ khóa slot có thể tạo ảnh chụp cũ và dẫn đến vượt sức chứa. M05 cung cấp `VisitCapacityReadService` chỉ đọc bảng `medical_visit`; không có migration mới.
