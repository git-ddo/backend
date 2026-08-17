ALTER TABLE evaluation_runs
    ADD COLUMN analysis_id UUID,
    ADD COLUMN evidence_snapshot JSONB;

UPDATE evaluation_runs
SET analysis_id = gen_random_uuid()
WHERE analysis_id IS NULL;

UPDATE evaluation_runs
SET status = 'COLLECTING'
WHERE status = 'RUNNING';

ALTER TABLE evaluation_runs
    ALTER COLUMN analysis_id SET NOT NULL;

ALTER TABLE evaluation_runs
    ADD CONSTRAINT uk_evaluation_run_analysis_id UNIQUE (analysis_id);
