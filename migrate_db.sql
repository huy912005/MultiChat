-- Migration: them cot roomCode va maUser vao bang Room
-- Chay tren Cloud server: mysql -u chatuser -p ChatAppDB < migrate_db.sql

ALTER TABLE Room 
  ADD COLUMN IF NOT EXISTS roomCode VARCHAR(5) UNIQUE DEFAULT NULL COMMENT 'Ma phong 5 ky tu',
  ADD COLUMN IF NOT EXISTS maUser INT DEFAULT NULL COMMENT 'Chu phong (FK -> User)';

-- Them chi muc de tim kiem nhanh
CREATE INDEX IF NOT EXISTS idx_room_code ON Room(roomCode);

-- Dam bao cot trangThai trong Message ton tai
ALTER TABLE Message 
  MODIFY COLUMN trangThai ENUM('SENT','SEEN') DEFAULT 'SENT';

SELECT 'Migration hoan thanh!' AS status;
