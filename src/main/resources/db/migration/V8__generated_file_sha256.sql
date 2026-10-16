-- 내용 식별자를 위한 준비 단계. 기존 바이트/JSON 참조의 변환은 .claude/scripts/migrate-file-hashes.py 가 맡는다.
ALTER TABLE generated_files ADD COLUMN sha256 varchar(64);
CREATE INDEX idx_generated_files_sha256 ON generated_files (sha256);
