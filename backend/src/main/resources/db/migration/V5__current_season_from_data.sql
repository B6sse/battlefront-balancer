-- The imported data has matches and season stats for a newer season than current_season lists, so the backend
-- treated an older season as current (and new matches would have been recorded there). Add the newest season
-- found in the data. The import also inserted current_season ids without moving the id sequence, which made
-- "Start season" fail with a duplicate key; move the sequence past the existing ids first.
SELECT setval(pg_get_serial_sequence('current_season', 'id'), COALESCE((SELECT MAX(id) FROM current_season), 0) + 1, false);

INSERT INTO current_season (season)
SELECT newest
FROM (
    SELECT GREATEST(
        COALESCE((SELECT MAX(season) FROM ranked_pstats), 0),
        COALESCE((SELECT MAX(season) FROM ranked_matches), 0)
    ) AS newest
) data
WHERE data.newest > COALESCE((SELECT MAX(season) FROM current_season), 0);
