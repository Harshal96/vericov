-- Keep internal Supabase service-role passwords aligned with POSTGRES_PASSWORD.
\set pgpass `echo "$POSTGRES_PASSWORD"`

SELECT format('ALTER USER %I WITH PASSWORD %L', rolname, :'pgpass')
FROM pg_roles
WHERE rolname IN (
    'postgres',
    'supabase_admin',
    'authenticator',
    'pgbouncer',
    'supabase_auth_admin',
    'supabase_functions_admin',
    'supabase_storage_admin'
)
\gexec
