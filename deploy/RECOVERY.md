# Deployment recovery

## Deployment model

GitHub Actions builds and scans frontend and backend images.
Images are tagged with `sha-<full commit SHA>`.

Deployment is started manually through the `Deploy to Lightsail`
workflow. The selected commit must have successful CI on `main`.

Server directory: `/home/ubuntu/lilimi`.

A deployment creates:

- a PostgreSQL dump in `backups/`;
- a copy of `.env` in `backups/`.

The deployment updates `.env` with the selected image tag only after
the deployment checks succeed.

A failed deployment does not automatically roll back containers.
After failure, running images may differ from `IMAGE_TAG` in `.env`.
Always inspect both.

## Inspect the current state

Run on Lightsail:

```bash
cd /home/ubuntu/lilimi

grep '^IMAGE_TAG=' .env

sudo docker ps --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}'

sudo docker compose ps

ls -lt backups/
```

Do not print or commit the complete `.env` file or its backups.
They contain secrets.

## Recover application containers

Prefer redeploying a known working commit with successful CI through
the existing `Deploy to Lightsail` workflow.

Before selecting an older version:

1. Check that both images are available in GHCR.
2. Check compatibility with the current database schema and data.
3. Review security findings for that version.
4. Confirm that the current database has a usable backup.

Matching migration files alone do not prove full application
compatibility. If migrations differ, review every change before
attempting a rollback.

Do not restore the database merely because a container deployment
failed. Application rollback and database recovery are separate
operations.

After recovery, confirm:

- frontend and backend run the intended image tags;
- both containers are healthy;
- the public catalogue and services page work;
- anonymous requests to administrator APIs return 401;
- POST requests without a CSRF token return 403;
- administrator login and the affected functionality work.

If the recovery fails, inspect container logs and running image tags.
Do not assume that `.env` describes the currently running containers.

## Restore-check a database backup

Test a backup in a separate database before relying on it.

Run on Lightsail. Replace the backup filename with an existing dump:

```bash
(
  set -euo pipefail
  cd /home/ubuntu/lilimi

  backup_file="backups/REPLACE_WITH_BACKUP_FILENAME.dump"
  test_db="lilimi_restore_check_$(date -u +%Y%m%d_%H%M%S)_$$"

  test -s "$backup_file"

  sudo docker compose exec -T postgres \
    createdb -U lilimi -T template0 "$test_db"

  echo "Test database: $test_db"

  sudo docker compose exec -T postgres \
    pg_restore -U lilimi -d "$test_db" \
    --exit-on-error --single-transaction < "$backup_file"

  sudo docker compose exec -T postgres \
    psql -U lilimi -d "$test_db" -v ON_ERROR_STOP=1 \
    -c "SELECT count(*) AS public_tables FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE';" \
    -c "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;" \
    -c "SELECT count(*) AS products FROM products;"

  echo "RESTORE CHECK PASSED"

  sudo docker compose exec -T postgres \
    dropdb -U lilimi "$test_db"
)
```

If the check fails, the test database may remain for investigation.
Remove only that exact test database after checking its name.

This check confirms that the dump can be restored and that the listed
queries succeed. It does not test every application feature.

## Production database recovery

Production database restoration has not been rehearsed.

Before restoring production:

- identify the incident and select the recovery point;
- account for orders, inquiries and other changes made after the backup;
- stop application writes;
- preserve the current database in a new backup;
- restore and validate the selected dump in a separate database;
- choose application images compatible with the restored schema;
- prepare and review the exact database replacement procedure.

Do not run `docker compose down -v`: it can remove persistent data.
Do not edit applied Flyway migrations or disable validation to bypass
a recovery error.

## Verification record — 2026-10-12

Successfully restored:

`backups/lilimi-before-deploy-20261012-120007.dump`

The restored database contained:

- 12 public tables;
- 5 products;
- successful migration history through V17.

The test database was removed after verification.

Both previous application images were available under:

`sha-d35ae24eefad14717d294a1745da55caf57e0b9e`

Migration files V1–V17 had identical SHA256 hashes in that backend
and the deployed backend:

`sha-72478558ef599fcae467d9e40c12650bffc7494b`

This records image availability and migration equality at the time
of checking. Application rollback was not executed.
