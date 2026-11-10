# M06.05 — Chuyển kho và chuyển nhóm tồn

`POST /api/v1/pharmacy/stock-documents/transfers` dành cho PHARMACIST đang hoạt động, cần CSRF, trả 201 và phiếu TRANSFER DRAFT. Body: `{"occurredAt":"2026-11-10T10:00:00+07:00","stockLocation":"MAIN","stockBucket":"AVAILABLE","destinationLocation":"COUNTER","destinationBucket":"AVAILABLE","note":null,"releaseConfirmed":false,"items":[{"batchId":1,"quantity":2.000}]}`.

Nguồn và đích là MAIN/COUNTER và AVAILABLE/QUARANTINE; không được trùng hoàn toàn. Chuyển từ AVAILABLE sang QUARANTINE cần lý do. Chuyển từ QUARANTINE về AVAILABLE cần `releaseConfirmed=true`, lý do xử lý và lô ACTIVE, thuốc đang hoạt động, còn hạn tại ngày ghi sổ `Asia/Ho_Chi_Minh`. Xác nhận được lưu cùng nội dung phiếu; bước ghi sổ kiểm tra lại sau khi khóa lô. Chuyển giữa vị trí nhưng giữ nguyên nhóm không yêu cầu xác nhận xử lý cách ly.

Phiếu nháp không đổi tồn. `POST /api/v1/pharmacy/stock-documents/{id}/post` trừ nguồn và cộng đích trong cùng giao dịch; nguồn không đủ trả 409. Tổng vật lý toàn viện không đổi. Phiếu POSTED bất biến; sửa sai bằng phiếu mới có ghi mã tham chiếu trong ghi chú. Không có migration mới.
