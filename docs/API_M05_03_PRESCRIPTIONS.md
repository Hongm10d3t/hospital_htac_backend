# M05.03 — Soạn và phát hành đơn thuốc

Các API dưới `/api/v1/doctor` chỉ dành cho bác sĩ đang hoạt động, được phân công lượt khám IN_PROGRESS hoặc COMPLETED. POST cần CSRF. Thuốc phải tồn tại và đang hoạt động tại thời điểm tạo đơn và khi phát hành; M06 khóa các dòng thuốc trong khi kiểm tra để tránh đổi trạng thái đồng thời.

- `POST /visits/{visitId}/prescriptions`: tạo đơn DRAFT, trả 201. Body `{"note":"<tùy chọn>","items":[{"medicineId":1,"quantity":2.500,"usageInstruction":"<hướng dẫn>"}]}`. Có 1–100 dòng, mỗi thuốc tối đa một lần, lượng dương tối đa 3 chữ số thập phân theo `base_unit`, hướng dẫn không trắng.
- `GET /visits/{visitId}/prescriptions`: danh sách đơn của bác sĩ trên lượt được phân công.
- `GET /prescriptions/{prescriptionId}`: chi tiết đơn, trạng thái và dòng thuốc.
- `POST /prescriptions/{prescriptionId}/issue`: DRAFT → ISSUED, ghi `issuedAt` từ backend. Gửi lặp giữ nguyên thời điểm và không tạo đơn khác. Đơn hủy trả 409 `PRESCRIPTION_FINAL`.

Đơn sai lượt/bác sĩ trả 404 hoặc 409 `VISIT_NOT_PRESCRIBABLE`; thuốc ngừng hoạt động trả 409 `MEDICINE_INACTIVE`; dòng trùng/lượng sai trả 400. M05 cung cấp `IssuedPrescriptionService.lockForDispensing` cho M06 trong cùng giao dịch: chỉ đơn ISSUED được cấp thuốc và khóa đơn cho đến khi giao dịch cấp phát hoàn tất. Tạo/phát hành đơn không ghi `stock_document` hay thay tồn; kiểm chứng số dư sau ghi sổ thuộc M06. Không có migration mới.
