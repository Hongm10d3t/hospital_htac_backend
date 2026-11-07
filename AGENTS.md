# Hướng dẫn Agent cho backend HTAC

## Trước khi nhận việc

1. Đọc `docs/PHAM_VI_DU_AN.md`, `docs/DATABASE.md`, `docs/PLAN_BACKEND.md`, `docs/SCHEMA_DECISIONS.md`, `BACKEND_RULES.md` và tài liệu API liên quan.
2. Xác định mã gói trong plan, gói tiên quyết, bảng/module sở hữu, file dự kiến sửa và tiêu chí nghiệm thu.
3. Nếu tài liệu mâu thuẫn hoặc quy tắc nghiệp vụ chưa đủ rõ, ghi rõ điều kiện và quyết định cần chốt trước khi viết phần phụ thuộc. Không tự mở rộng phạm vi sang phân hệ khác.

## Trong khi làm

- Chỉ một module sở hữu entity và repository ghi của mỗi bảng. Dùng service công khai của module sở hữu khi phối hợp; đặt transaction ở service điều phối luồng sử dụng.
- Dùng DTO cho API; không trả entity JPA, mật khẩu băm, thông tin định danh ngoài phạm vi quyền hoặc stack trace.
- Dùng migration Flyway mới cho thay đổi schema. Không sửa migration đã áp dụng.
- Viết kiểm thử cho quy tắc mới, nhất là quyền đối tượng, lỗi giữa giao dịch, gửi lặp và yêu cầu đồng thời ở các luồng giữ suất/cấp số/ghi sổ/nhận hàng.
- Không ghi mật khẩu hoặc dữ liệu bệnh nhân vào source, log hay tài liệu ví dụ.

## Khi bàn giao một gói

- Chạy `./mvnw test` hoặc `mvnw.cmd test`; phần phụ thuộc SQL phải chạy trên MySQL kiểm thử riêng.
- Báo file thay đổi, API/schema mới, lệnh và kết quả kiểm tra, mục nghiệm thu đạt/chưa đạt, quyết định còn phụ thuộc.
- Cập nhật trạng thái checklist chỉ khi có bằng chứng thực tế. Tài liệu kế hoạch không tự chứng minh chức năng đã hoàn thành.

Chỉ dẫn trực tiếp của người dùng trong phiên làm việc được ưu tiên khi khác hướng dẫn ở đây.
