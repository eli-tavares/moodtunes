-- Optional: how strong the feeling was, 1 (barely) to 5 (very strong). NULL when the person did not say.
ALTER TABLE check_in ADD COLUMN intensity SMALLINT;
ALTER TABLE check_in ADD CONSTRAINT chk_check_in_intensity CHECK (intensity IS NULL OR intensity BETWEEN 1 AND 5);
