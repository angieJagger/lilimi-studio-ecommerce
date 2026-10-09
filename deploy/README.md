# Docker setup and AWS deployment

Lilimi Studio runs as four Docker Compose services:

- `postgres` — PostgreSQL database;
- `backend` — Spring Boot REST API;
- `frontend` — Angular SSR application;
- `gateway` — Caddy reverse proxy and HTTPS termination.

The live demonstration is available at [lilimistudio.com](https://lilimistudio.com).

Orders and project inquiries are stored in the database. Payments, transactional emails, order fulfilment, and digital file delivery are not implemented. Use fictional contact details when testing.

## Run the complete stack locally

Run commands from the repository root with Docker Desktop running in Linux container mode.

For first-time setup:

```powershell
Copy-Item .env.example .env
```

Replace the database password placeholder in `.env`. If the file already exists, keep it rather than overwriting it.

Start the application:

```powershell
docker compose up -d --build --wait
```

Open [localhost:8088/en](http://localhost:8088/en).

The gateway forwards `/api` requests to Spring Boot and other requests to Angular SSR. The backend and database do not have published host ports in this stack.

Node.js and Java are provided by the build containers and are not required on the host for this setup.

### Check or stop the stack

```powershell
docker compose ps
docker compose logs --tail 100 backend frontend gateway
docker compose down
```

`docker compose down` preserves the database volume. Adding `--volumes` deletes the stack's persistent volumes, including database data.

Changing `DB_PASSWORD` in `.env` does not change the password of an already initialized PostgreSQL database.

## Database-only development stack

The Compose file in `backend` starts only PostgreSQL for development with locally running Java and Angular applications.

It publishes PostgreSQL on `127.0.0.1:15432`.

The database-only stack and complete application stack use separate database volumes. Data created in one is not automatically available in the other.

See the root README for the local development setup.

## Verify before deployment

Frontend, from the repository root:

```powershell
npm test -- --watch=false
npm run build
```

Backend, from `backend`, with Docker running:

```powershell
.\mvnw.cmd verify
```

Backend integration tests use a separate PostgreSQL container.

The backend Docker build skips tests because integration tests require a Docker daemon. A successful image build does not replace running the test suite.

## Current AWS deployment

The demonstration runs on an AWS Lightsail Linux instance using Docker Compose.

Caddy serves the application over HTTPS at `lilimistudio.com` and persists certificate data in Docker volumes.

Only the gateway publishes application ports `80` and `443`. PostgreSQL, Spring Boot, and Angular SSR communicate through the internal Docker network.

The server application directory is:

```text
/home/ubuntu/lilimi
```

It contains the deployment Compose configuration, `deploy/Caddyfile`, and server-specific environment configuration.

Application source code is not required on the server. The current deployment process builds images locally, transfers them to Lightsail, and recreates the application containers.

### Server configuration

The deployment uses a server-only `.env` containing the database password and settings such as:

```dotenv
APP_HOSTS=lilimistudio.com,localhost,127.0.0.1
SITE_ADDRESS=lilimistudio.com
HTTP_BIND=0.0.0.0
HTTP_PORT=80
HTTPS_PORT=443
SESSION_COOKIE_SECURE=true
```

The backend environment must include:

```yaml
SERVER_FORWARD_HEADERS_STRATEGY: framework
SERVER_SERVLET_SESSION_COOKIE_SECURE: ${SESSION_COOKIE_SECURE:-false}
```

The secure session cookie setting is enabled on the HTTPS deployment and disabled for local HTTP development.

Keep passwords, private keys, and real environment files out of Git and Docker build contexts.

Do not overwrite the server Compose configuration with a local development configuration without reviewing its image names, port bindings, and environment settings.

## Manual deployment

### 1. Build and export the images

From the repository root:

```powershell
docker build -t lilimi-demo-backend:latest ./backend
docker build -t lilimi-demo-frontend:latest .
docker image save -o lilimi-demo-images.tar lilimi-demo-backend:latest lilimi-demo-frontend:latest
```

Build images for the server's CPU architecture. The current Lightsail instance uses x86-64.

Transfer the archive using SCP. Replace the placeholders with your private key path and server address:

```powershell
scp -i "<private-key-path>" .\lilimi-demo-images.tar ubuntu@<server-ip>:/home/ubuntu/
```

### 2. Back up the database

On the server:

```bash
cd /home/ubuntu/lilimi
mkdir -p backups
chmod 700 backups

backup_file="backups/lilimi-before-deploy-$(date +%Y%m%d-%H%M%S).dump"

if sudo docker compose exec -T postgres pg_dump -U lilimi -d lilimi -Fc > "$backup_file"; then
    chmod 600 "$backup_file"
    ls -lh "$backup_file"
else
    echo "Backup failed. Stop deployment."
fi
```

Continue only if the backup command succeeds and the archive is non-empty. Backups contain application data and must be kept private.

A backup stored on the same instance helps with recovery from deployment mistakes but does not protect against losing the instance. Off-server backup storage and restore verification remain planned work.

### 3. Preserve the previous images and load the new ones

```bash
sudo docker image tag lilimi-demo-backend:latest lilimi-demo-backend:previous
sudo docker image tag lilimi-demo-frontend:latest lilimi-demo-frontend:previous

sudo docker image load -i /home/ubuntu/lilimi-demo-images.tar
```

### 4. Update the backend

```bash
sudo docker compose up -d --no-build --no-deps --force-recreate --wait --wait-timeout 180 backend
```

Flyway applies pending database migrations during backend startup.

Continue only after the backend becomes healthy. If startup fails, inspect the logs:

```bash
sudo docker compose logs --tail 150 backend
```

### 5. Update the frontend

```bash
sudo docker compose up -d --no-build --no-deps --force-recreate --wait --wait-timeout 180 frontend
```

After the frontend becomes healthy:

```bash
sudo docker compose restart gateway
sudo docker compose ps
```

### 6. Check the application

Verify:

- English and Polish pages;
- product configuration and cart persistence;
- test order submission and confirmation;
- administrator login and logout;
- order details and status updates;
- project inquiry submission and administrator inquiry details.

Use fictional contact details.

## Recovery considerations

Previous application images are retained under the `previous` tags.

Reverting an image does not reverse database migrations. Before restoring an older application version, check whether it is compatible with the current database schema.

Database restoration is a separate operation and may remove data created after the backup. Do not restore a database automatically as part of an image rollback.

## Administrator accounts

Administrator accounts are provisioned through the dedicated `provision-admin` profile. There is no public administrator registration endpoint.

For interactive provisioning on the server:

```bash
sudo docker compose run --rm --no-deps --entrypoint java backend -jar app.jar --spring.profiles.active=provision-admin
```

The command prompts for account details. Existing accounts remain in the persistent database and do not need to be recreated after deployment.

## Planned CI/CD

The next deployment improvement is GitHub Actions for:

1. frontend and backend verification;
2. building and publishing Docker images to GitHub Container Registry;
3. deploying a selected version to Lightsail;
4. database backup and application health checks during deployment.

Until this workflow is configured and verified, deployment remains manual.
