-- 기존 리소스에는 당시의 서비스 구분이 없으므로 사용자별 기본 서비스에 연결한다.
ALTER TABLE generation_jobs ADD COLUMN workspace_uuid uuid;
UPDATE generation_jobs j
SET workspace_uuid = (SELECT w.uuid FROM workspaces w WHERE w.owner_user_uuid = j.owner_user_uuid AND w.is_default);
ALTER TABLE generation_jobs ALTER COLUMN workspace_uuid SET NOT NULL;
CREATE INDEX idx_generation_jobs_workspace ON generation_jobs (workspace_uuid);

ALTER TABLE uploaded_files ADD COLUMN workspace_uuid uuid;
UPDATE uploaded_files f
SET workspace_uuid = (SELECT w.uuid FROM workspaces w WHERE w.owner_user_uuid = f.owner_user_uuid AND w.is_default);
ALTER TABLE uploaded_files ALTER COLUMN workspace_uuid SET NOT NULL;
ALTER TABLE uploaded_files DROP CONSTRAINT uk_uploaded_files_owner_sha256;
ALTER TABLE uploaded_files ADD CONSTRAINT uk_uploaded_files_workspace_sha256 UNIQUE (workspace_uuid, sha256);

ALTER TABLE provider_calls ADD COLUMN workspace_uuid uuid;
UPDATE provider_calls c
SET workspace_uuid = (SELECT w.uuid FROM workspaces w WHERE w.owner_user_uuid = c.owner_user_uuid AND w.is_default);
ALTER TABLE provider_calls ALTER COLUMN workspace_uuid SET NOT NULL;
CREATE INDEX idx_provider_calls_workspace_called ON provider_calls (workspace_uuid, called_at);
