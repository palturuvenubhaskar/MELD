-- V10: authentication and workspace security (M4)
-- Source of truth: docs/DATABASE.md sections 5 and 6.
--
-- Emails are stored normalized (trimmed, lowercase). The functional unique
-- index makes that normalization enforceable even if a writer bypasses the
-- service layer.
CREATE UNIQUE INDEX uq_users_email_lower ON users (lower(email));

-- Listing a user's workspaces starts from their memberships, so index that
-- side of the composite primary key.
CREATE INDEX idx_workspace_members_user_id ON workspace_members (user_id);
