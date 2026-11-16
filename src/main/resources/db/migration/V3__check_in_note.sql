-- Optional: the person's own words for a check-in, kept only when they choose to (for sharing with their therapist),
-- and a marker for sentences the AI flagged as worrying. Both stay NULL/false unless the person ticks "keep my words".
ALTER TABLE check_in ADD COLUMN note VARCHAR(500);
ALTER TABLE check_in ADD COLUMN flagged BOOLEAN NOT NULL DEFAULT FALSE;
