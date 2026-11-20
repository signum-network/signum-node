-- Rows were written once per popped block with the current tip as height and a depth of 1;
-- from now on there is one row per rollback. The old rows cannot be repaired.
DELETE FROM fork_history;
