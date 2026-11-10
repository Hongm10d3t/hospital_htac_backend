# M06.01 — Danh mục thuốc và nhà cung cấp

Các API `/api/v1/pharmacy` yêu cầu tài khoản PHARMACIST đang hoạt động. POST/PUT cần CSRF. Danh mục dùng chung với đơn thuốc M05 và phiếu kho M06.

- `GET /medicines?term=&active=&page=&size=&sort=`: tra cứu thuốc có phân trang; `GET /medicines/{id}`: chi tiết.
- `POST /medicines`: tạo thuốc, trả 201. `PUT /medicines/{id}`: cập nhật. Body: `code`, `name`, `activeIngredient`, `strength`, `dosageForm`, `baseUnit`, `minimumStock`, `leadTimeDays`, `active`.
- `GET /suppliers?term=&active=&page=&size=&sort=` và `GET /suppliers/{id}`: tra cứu nhà cung cấp.
- `POST /suppliers`: tạo nhà cung cấp, trả 201. `PUT /suppliers/{id}`: cập nhật. Body: `code`, `name`, `contactName`, `phone`, `email`, `address`, `active`.
- `GET /api/v1/doctor/medicines?term=&page=&size=&sort=`: bác sĩ đang hoạt động tra cứu thuốc đang hoạt động để kê đơn.

Số lượng sử dụng `baseUnit`, tối đa ba chữ số thập phân; `minimumStock` và `leadTimeDays` không âm. Mã thuốc và mã nhà cung cấp duy nhất. Khi thuốc đã có dòng đơn, dòng đề xuất nhập hoặc lô, không thể đổi mã, hoạt chất, hàm lượng, dạng bào chế và đơn vị cơ sở; tạo mã thuốc mới để thay bản chất. Tên hiển thị, ngưỡng tồn, lead time và trạng thái có thể cập nhật, kể cả khi thuốc có lịch sử. Thuốc ngừng hoạt động vẫn tra được trong danh mục và hiển thị tên trong đơn cũ, nhưng không xuất hiện trong lựa chọn kê đơn. Chưa có migration mới.
