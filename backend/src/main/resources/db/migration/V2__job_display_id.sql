-- The job ID candidates see on the posting (e.g. Amdocs "213469"), as opposed to our dedup key external_id.
ALTER TABLE job ADD COLUMN display_id VARCHAR(64);
