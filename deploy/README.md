# Full application in Docker

Run commands from the repository root. Docker Desktop must be running.

```powershell
Copy-Item .env.example .env
# Replace DB_PASSWORD in .env with a long random password before starting.
docker compose up -d --build --wait
```

If `.env` already exists, keep it instead of copying over it.
Open http://localhost:8088/pl/products. The gateway forwards `/api` to Spring Boot
and other requests to the Angular SSR server. Node and Java are only needed inside
the build containers, not on the host.

The stack uses a separate `lilimi-demo` PostgreSQL volume, initialized by Flyway.
It does not reuse the database from `backend/compose.yaml`. Neither the database
nor the backend has a published host port. Local secret files are excluded from
the build contexts and from Git.

```powershell
docker compose ps
docker compose logs --tail 100 backend frontend gateway
docker compose down
```

`down` keeps database data. Do not add `--volumes` unless intentionally deleting
the demo database. Changing DB_PASSWORD does not change the password in an
existing PostgreSQL volume.

Images use multi-stage builds and non-root application users. Docker builds skip
backend tests because integration tests require a Docker daemon. Run the normal
test suites separately before releasing changes.

## AWS demo preparation

Deployment has not happened yet. A small Lightsail Linux instance is a candidate
for a low-traffic demo, subject to measured memory use and the selected AWS plan.
Build images locally or in CI; building Angular and Java on a small server can
exceed its memory. Transfer the built images with `docker save` / `docker load`,
or use an image registry. Keep all images on the server's CPU architecture.

Before public deployment:

1. Create the AWS account, enable MFA, review credits and set billing alerts.
   Alerts are not a hard spending cap. Confirm the monthly price before launch.
2. Choose one Linux server and install Docker Engine with Compose from official
   Docker instructions for that OS. Do not expose ports 5432, 8080 or 4000.
3. Point the chosen hostname to the server. Allow HTTP/HTTPS (80/443), and limit
   SSH to the administrator's IP address.
4. Copy compose.yaml, deploy/Caddyfile and the images to the server. Use a new
   server-only `.env` with a random database password and these settings:

   ```dotenv
   APP_HOSTS=demo.example.com,localhost,127.0.0.1
   SITE_ADDRESS=demo.example.com
   HTTP_BIND=0.0.0.0
   HTTP_PORT=80
   HTTPS_PORT=443
   ```

5. Start with `docker compose up -d --no-build --wait`. Caddy requests and renews
   HTTPS certificates when DNS and ports are configured correctly.
6. Verify Polish/English pages, direct product URLs, the cart and error handling.
   Show a visible demo notice before sharing the URL: no real orders or payments,
   and use test contact details only. Add the confirmed URL to the main README.

APP_HOSTS is a comma-separated allowlist for Angular SSR. Do not set it to `*`.
The demo is not a production shop: order submission and payments remain unfinished.
