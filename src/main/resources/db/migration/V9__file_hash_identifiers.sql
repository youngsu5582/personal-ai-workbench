-- 기존 데이터는 먼저 migrate-file-hashes.py 로 V8 까지 적용하고 변환한다.
-- 해시 없는 행이 남아 있으면 NOT NULL 검증이 실패하므로 기존 파일을 잃은 채 기동하지 않는다.
ALTER TABLE generated_files ALTER COLUMN sha256 SET NOT NULL;
ALTER TABLE uploaded_files ALTER COLUMN sha256 SET NOT NULL;
ALTER TABLE generated_files DROP COLUMN uuid;
ALTER TABLE uploaded_files DROP COLUMN uuid;
