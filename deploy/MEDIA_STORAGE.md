# Persistent product images

The database stores image metadata; image bytes live in a Docker named volume.
Both are required for recovery. Local uploads are not published through Git.

## Local Compose

The root Compose file mounts `product_media` at `/app/var/media`.
The backend image creates this directory owned by its non-root application user.
Docker initializes a new named volume from that directory on first attachment.
Do not use `docker compose down -v`: it deletes database and media volumes.

## Lightsail setup

Keep the existing server Compose file. Copy `deploy/compose.media.yaml` to
`/home/ubuntu/lilimi/compose.override.yaml`. If an override already exists, merge
its backend environment, volume mount and top-level volume declaration instead
of replacing it. The deploy workflow automatically loads this override.

Use a newly built backend image containing the media directory ownership setup.
An old backend image may not initialize an empty volume with writable ownership.
Before enabling uploads, validate the Compose configuration and deploy the new
image. Never expose the media directory through a separate static web server:
the API checks product visibility before serving image content.

After deployment, run on Lightsail:

```bash
cd /home/ubuntu/lilimi
sudo docker compose config --quiet
sudo docker compose exec -T backend sh -c 'test -d "$MEDIA_STORAGE_DIRECTORY" && test -w "$MEDIA_STORAGE_DIRECTORY"'
```

Upload an image using the live admin, save the product, and verify it on the
public product page. Recreate the backend through the deploy workflow, then
verify the same image again. A plain browser refresh does not prove persistence.

If production already contains uploaded images in a container writable layer,
copy them out before recreating that container and migrate them into the volume.
Do not assume that attaching a volume migrates existing uploads automatically.

## Consistent backup

The current deployment workflow backs up PostgreSQL and `.env` only. It does
not yet produce a complete media backup. Before a release with uploaded images,
take the following backup during a short maintenance window. Stopping backend
prevents image changes between the database dump and the media archive.

```bash
cd /home/ubuntu/lilimi
set -euo pipefail
umask 077
mkdir -p backups
stamp=$(date -u +%Y%m%d-%H%M%S)
sudo docker compose stop backend
trap 'sudo docker compose start backend' EXIT
sudo docker compose exec -T postgres pg_dump -U lilimi -d lilimi -Fc > "backups/lilimi-with-media-$stamp.dump"
sudo docker compose run --rm --no-deps -T --entrypoint tar backend -C /app/var/media -czf - . > "backups/media-$stamp.tar.gz"
test -s "backups/lilimi-with-media-$stamp.dump"
tar -tzf "backups/media-$stamp.tar.gz" > /dev/null
sudo docker compose start backend
trap - EXIT
```

Keep the archive and matching dump together, copy them off the server, and test
restoration in an isolated environment. Restore both while backend is stopped;
restore media as the backend application user so uploaded files remain writable.
Do not restore media over an unrelated database snapshot.
