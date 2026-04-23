-- ============================================================
-- Script don dep DB: giu lai phong Hoc Thuat + Giai Tri
-- Chay: mysql -u chatuser -pChatApp@123 ChatAppDB < cleanup_db.sql
-- ============================================================

-- Buoc 1: Lay ID cua 2 phong can giu
-- (Gia su la ID 1 = Sanh Chung/Hoc Thuat, can check truoc)
-- Tim ID phong Hoc Thuat va Giai Tri
SELECT maRoom, tenRoom FROM Room;

-- Buoc 2: Xoa Message cua cac phong KHAC (giu lai Hoc Thuat va Giai Tri)
DELETE FROM Message 
WHERE maRoom NOT IN (
    SELECT maRoom FROM (SELECT maRoom FROM Room WHERE tenRoom IN ('Phong Hoc Thuat', 'Hoc Thuat', 'Giai Tri', 'Giải Trí', 'Phòng Học Thuật')) AS keep
);

-- Buoc 3: Xoa cac phong khac (giu lai Hoc Thuat va Giai Tri)
DELETE FROM UserRoom 
WHERE maRoom NOT IN (
    SELECT maRoom FROM (SELECT maRoom FROM Room WHERE tenRoom IN ('Phong Hoc Thuat', 'Hoc Thuat', 'Giai Tri', 'Giải Trí', 'Phòng Học Thuật')) AS keep
);

DELETE FROM Room 
WHERE tenRoom NOT IN ('Phong Hoc Thuat', 'Hoc Thuat', 'Giai Tri', 'Giải Trí', 'Phòng Học Thuật');

-- Buoc 4: Xoa tat ca User (dat trang thai OFFLINE, xoa UserRoom truoc)
DELETE FROM UserRoom;
DELETE FROM User WHERE tenUser != 'admin';

-- Reset auto increment cho Room (tuy chon)
-- ALTER TABLE Room AUTO_INCREMENT = 10;

SELECT 'Don dep hoan thanh!' AS status;
SELECT maRoom, tenRoom FROM Room;
SELECT COUNT(*) AS so_user FROM User;
