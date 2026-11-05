-- V1: the starting point. Tables arrive in later migrations (technical-plan.md §7), each in its
-- own V{n}__name.sql. Flyway migrations only go forward: never edit a file once it has run
-- anywhere (README §15, ulap-reference.md §10).
SELECT 1;
