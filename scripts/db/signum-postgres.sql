-- Sets up PostgreSQL for Signum Node on any OS: login role, database and sync tuning.
-- See DB_SETUP_POSTGRESQL.md. Safe to run again.
--
-- Run it with psql as a superuser:
--   Linux:   sudo -u postgres psql -v password='...' < scripts/db/signum-postgres.sql
--   macOS:   psql -d postgres -v password='...' -f scripts/db/signum-postgres.sql
--   Windows: psql -U postgres -v password="..." -f scripts\db\signum-postgres.sql
--
-- Options (each as -v name=value):
--   password        password for the node's database user (required)
--   db              database name (default: signum; e.g. signum_testnet)
--   user            database user (default: signumnode)
--   tune            off = only create role and database (default: on)
--   shared_buffers  memory for data and indexes, about 25% of the RAM (default: 1GB)

\set ON_ERROR_STOP on

\if :{?password}
\else
DO $$ BEGIN RAISE EXCEPTION 'Set a password for the node''s database user: psql -v password=... '; END $$;
\endif
\if :{?db}
\else
\set db signum
\endif
\if :{?user}
\else
\set user signumnode
\endif
\if :{?tune}
\else
\set tune on
\endif
\if :{?shared_buffers}
\else
\set shared_buffers 1GB
\endif

-- Role and database
SELECT format('CREATE ROLE %I LOGIN', :'user')
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = :'user') \gexec
SELECT format('ALTER ROLE %I LOGIN PASSWORD %L', :'user', :'password') \gexec
SELECT format('CREATE DATABASE %I OWNER %I', :'db', :'user')
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = :'db') \gexec

-- Sync tuning: no disk flush per commit, fewer checkpoints, more memory.
-- A crash can lose the last fraction of a second of commits; the node syncs those blocks again.
\if :tune
ALTER SYSTEM SET synchronous_commit = off;
ALTER SYSTEM SET max_wal_size = '4GB';
ALTER SYSTEM SET checkpoint_timeout = '15min';
ALTER SYSTEM SET checkpoint_completion_target = 0.9;
ALTER SYSTEM SET shared_buffers = :'shared_buffers';
SELECT pg_reload_conf() AS reloaded \gset
\echo
\echo 'Tuning saved. Restart PostgreSQL so that shared_buffers takes effect.'
\endif

\set url 'DB.Url=jdbc:postgresql://localhost:5432/' :db
\set username 'DB.Username=' :user
\echo
\echo 'Done. Add to conf/node.properties:'
\echo :url
\echo :username
\echo 'DB.Password=<the password you chose>'
