ALTER TABLE generation_templates ADD COLUMN workspace_uuid UUID;

UPDATE generation_templates t
SET workspace_uuid = (
    SELECT w.uuid FROM workspaces w
    WHERE w.owner_user_uuid = t.owner_user_uuid AND w.is_default = true
);

ALTER TABLE generation_templates ALTER COLUMN workspace_uuid SET NOT NULL;
DROP INDEX idx_generation_templates_owner;
CREATE INDEX idx_generation_templates_workspace ON generation_templates (owner_user_uuid, workspace_uuid, created_at DESC);
