-- 공개 파일 식별자를 UUID 에서 SHA-256 으로 바꾼다 (ADR 0001).
-- 운영 배포 전이라 기존 행을 변환하지 않는다. 해시 없는 행이 있으면 실패해, 파일을 잃은 채 기동하지 않는다.
ALTER TABLE generated_files ADD COLUMN sha256 varchar(64) NOT NULL;
CREATE INDEX idx_generated_files_sha256 ON generated_files (sha256);
ALTER TABLE uploaded_files ALTER COLUMN sha256 SET NOT NULL;
ALTER TABLE generated_files DROP COLUMN uuid;
ALTER TABLE uploaded_files DROP COLUMN uuid;
