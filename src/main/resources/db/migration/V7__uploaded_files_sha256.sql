-- 기존 행의 바이트를 추측해 채우지 않는다. null 은 기존 UUID 경로와 함께 남는다.
ALTER TABLE uploaded_files ADD COLUMN sha256 varchar(64);
ALTER TABLE uploaded_files ADD CONSTRAINT uk_uploaded_files_owner_sha256 UNIQUE (owner_user_uuid, sha256);
