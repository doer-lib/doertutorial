CREATE INDEX IF NOT EXISTS tasks_status_idx ON tasks (status, created);
CREATE INDEX IF NOT EXISTS tasks_failing_idx ON tasks (status, modified) WHERE failing_since IS NOT NULL;
CREATE INDEX IF NOT EXISTS tasks_in_progress_idx ON tasks (status) WHERE in_progress;

