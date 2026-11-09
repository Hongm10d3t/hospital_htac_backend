# M03.01 — Tạo lịch khám

ADMIN dùng session và CSRF để tạo lịch. M03 sở hữu `clinic_slot`; không có migration mới vì bảng và chỉ mục đã tồn tại trong V1. Mọi thời điểm lưu UTC, ngày/giờ buổi hiểu theo `Asia/Ho_Chi_Minh`.

## Tạo khung đơn

`POST /api/v1/admin/clinic-slots` với body `{"roomId":1,"doctorId":2,"startAt":"2030-01-01T08:00:00+07:00","endAt":"2030-01-01T08:15:00+07:00","capacity":2}`. Thành công trả 201, `Location: /api/v1/admin/clinic-slots/{id}`, body `ClinicSlotResponse` gồm ID, phòng, bác sĩ, giờ bắt đầu/kết thúc, sức chứa và trạng thái `OPEN`.

## Tạo nhiều khung trong tuần

`POST /api/v1/admin/clinic-slots/sessions` với body `{"roomId":1,"doctorId":2,"dates":["2030-01-01","2030-01-03"],"startTime":"08:00","endTime":"09:00","durationMinutes":15,"capacity":2}`. `dates` có 1–7 ngày khác nhau trong cùng tuần (thứ Hai đến Chủ nhật), giờ bắt đầu/kết thúc trong cùng ngày và khoảng giờ chia hết cho thời lượng khung. Thời lượng mỗi khung 1–240 phút. Trả 201 với danh sách các khung theo thời gian tăng dần, `Location` của khung đầu. Nếu bất kỳ khung nào không hợp lệ hoặc giao lịch, toàn bộ lô rollback.

## Quy tắc phân công

Khung phải bắt đầu trong tương lai, kết thúc sau bắt đầu, sức chứa dương. Tài khoản phải còn hoạt động với role DOCTOR, có hồ sơ bác sĩ; phòng và chuyên khoa còn hoạt động; chuyên khoa bác sĩ trùng chuyên khoa phòng. Các khung của cùng phòng **hoặc** cùng bác sĩ dùng khoảng nửa mở `[startAt,endAt)`: khung liền kề được phép, giao nhau bị từ chối 409 `SLOT_OVERLAP`. Mọi slot, kể cả `CLOSED`, vẫn giữ chỗ trên lịch để bảo toàn lịch sử.

Giao dịch khóa lần lượt tài khoản bác sĩ, hồ sơ bác sĩ, phòng trước khi kiểm tra giao lịch và ghi. Hai yêu cầu cạnh tranh cùng phòng hoặc bác sĩ được tuần tự hóa; yêu cầu sau kiểm tra lại và bị từ chối nếu giao nhau. Tạo lặp cùng khung trả 409, không tạo bản ghi thứ hai. Dữ liệu ngừng hoạt động trả 409 với mã tương ứng; sai chuyên khoa trả `SPECIALTY_MISMATCH`; ID thiếu trả 404; body sai trả 400; sai quyền hoặc CSRF trả 403.
