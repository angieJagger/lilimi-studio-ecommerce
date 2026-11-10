"""Create an off-host Lilimi backup and verify database and media restoration.

Run with Python 3.12 or newer. Backup folders contain private customer data.
Access is restricted to the current Windows user.
"""
from __future__ import annotations

import argparse
import getpass
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import shlex
import shutil
import subprocess
import tarfile
import time
from uuid import uuid4


BACKUP_FILES = (
    "database.dump", "media.tar.gz",
    "backend-image.txt", "frontend-image.txt", "SHA256SUMS",
)


def run(arguments, *, data=None, output=None, timeout=1200):
    result = subprocess.run(
        arguments, input=data, stdout=output or subprocess.PIPE,
        stderr=subprocess.PIPE, check=False, timeout=timeout,
    )
    if result.returncode:
        diagnostic = result.stderr.decode("utf-8", errors="replace").strip()
        raise RuntimeError(f"{arguments[0]} failed ({result.returncode}): {diagnostic}")
    return result.stdout or b""


def restrict_access(directory):
    if os.name == "nt":
        user = os.environ.get("USERNAME", getpass.getuser())
        domain = os.environ.get("USERDOMAIN")
        account = f"{domain}\\{user}" if domain else user
        run(["icacls", str(directory), "/inheritance:r", "/grant:r", f"{account}:(OI)(CI)F"])
    else:
        directory.chmod(0o700)


def verify_checksums(directory):
    expected_names = set(BACKUP_FILES) - {"SHA256SUMS"}
    recorded_names = set()
    for line in (directory / "SHA256SUMS").read_text(encoding="utf-8").splitlines():
        digest, filename = line.split(maxsplit=1)
        filename = filename.lstrip("*")
        if filename not in expected_names or filename in recorded_names:
            raise ValueError("Unexpected file in backup checksum manifest")
        recorded_names.add(filename)
        with (directory / filename).open("rb") as source:
            actual = hashlib.file_digest(source, "sha256").hexdigest()
        if actual != digest:
            raise ValueError(f"Backup checksum mismatch for {filename}")
    if recorded_names != expected_names:
        raise ValueError("Backup checksum manifest is incomplete")


def create_backup(args):
    if not re.fullmatch(r"[a-zA-Z0-9_.-]+@[a-zA-Z0-9_.-]+", args.host):
        raise ValueError("Use an SSH destination in user@host format")
    identity = args.identity.expanduser().resolve(strict=True)
    snapshot = time.strftime("%Y%m%dT%H%M%SZ", time.gmtime()) + "-" + uuid4().hex[:8]
    destination = args.destination.resolve() / snapshot
    destination.mkdir(parents=True, exist_ok=False)
    restrict_access(destination)

    script = """
set -euo pipefail
umask 077
cd __REMOTE_DIRECTORY__
snapshot=__SNAPSHOT__
exec 9>.deployment.lock
flock -n 9 || { echo "Another backup or deployment is running." >&2; exit 1; }
compose() { sudo -n docker compose "$@" < /dev/null; }
backend_id=$(compose ps -q backend)
frontend_id=$(compose ps -q frontend)
test -n "$backend_id"
test -n "$frontend_id"
test "$(sudo -n docker inspect --format '{{.State.Running}}' "$backend_id")" = true
backend_image=$(sudo -n docker inspect --format '{{.Config.Image}}' "$backend_id")
target="backups/offhost-$snapshot"
mkdir -m 700 "$target"
printf '%s\\n' "$backend_image" > "$target/backend-image.txt"
sudo -n docker inspect --format '{{.Config.Image}}' "$frontend_id" > "$target/frontend-image.txt"

trap 'compose start backend >&2' EXIT
compose stop backend >&2
compose exec -T postgres pg_dump -U lilimi -d lilimi -Fc > "$target/database.dump"
sudo -n docker run --rm --network none --volumes-from "$backend_id:ro" \
  --entrypoint tar "$backend_image" -czf - -C /app/var/media . > "$target/media.tar.gz"
test -s "$target/database.dump"
test -s "$target/media.tar.gz"
tar -tzf "$target/media.tar.gz" > /dev/null
(
  cd "$target"
  sha256sum database.dump media.tar.gz backend-image.txt frontend-image.txt > SHA256SUMS
  chmod 600 ./*
)
compose start backend >&2
for attempt in $(seq 1 60); do
  if test "$(sudo -n docker inspect --format '{{.State.Health.Status}}' "$backend_id")" = healthy; then
    trap - EXIT
    printf 'BACKUP_READY=%s\\n' "$target"
    exit 0
  fi
  sleep 3
done
echo "Backend did not become healthy after the backup." >&2
exit 1
""".replace("__REMOTE_DIRECTORY__", shlex.quote(args.remote_directory)).replace(
        "__SNAPSHOT__", shlex.quote(snapshot)
    )
    options = ["-i", str(identity), "-o", "BatchMode=yes", "-o", "StrictHostKeyChecking=yes",
               "-o", "ConnectTimeout=15", "-o", "ServerAliveInterval=15",
               "-o", "ServerAliveCountMax=4"]
    print("Creating a consistent server backup.", flush=True)
    remote_output = run(
        ["ssh", *options, args.host, "bash -s"], data=script.encode("utf-8")
    ).decode("utf-8")
    remote_path = f"backups/offhost-{snapshot}"
    if f"BACKUP_READY={remote_path}" not in remote_output.splitlines():
        raise RuntimeError("The server did not confirm backup readiness")
    remote_directory = PurePosixPath(args.remote_directory) / remote_path
    print("Backend healthy. Downloading the backup.", flush=True)
    for filename in BACKUP_FILES:
        run(["scp", *options, f"{args.host}:{shlex.quote(str(remote_directory / filename))}",
             str(destination / filename)])
    verify_checksums(destination)
    (destination / "backup-complete.json").write_text(
        json.dumps({"snapshot": snapshot, "host": args.host, "checksumsVerified": True}, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"BACKUP COMPLETE {destination}", flush=True)
    if args.check:
        check_backup(destination)


def check_backup(directory):
    directory = directory.resolve(strict=True)
    verify_checksums(directory)
    suffix = uuid4().hex
    container = f"lilimi-restore-check-{suffix}"
    volume = f"lilimi-restore-check-{suffix}"
    restored_media = directory / f"restore-check-media-{suffix}"
    restored_media.mkdir()
    created_container = False
    created_volume = False
    try:
        run(["docker", "volume", "create", volume])
        created_volume = True
        run([
            "docker", "run", "--detach", "--rm", "--pull", "never", "--network", "none",
            "--name", container, "--mount", f"type=volume,source={volume},target=/var/lib/postgresql/data",
            "--mount", f"type=bind,source={directory},target=/backup,readonly",
            "-e", "POSTGRES_USER=lilimi", "-e", "POSTGRES_DB=lilimi",
            "-e", "POSTGRES_HOST_AUTH_METHOD=trust", "postgres:17",
        ])
        created_container = True
        for attempt in range(60):
            probe = subprocess.run(
                ["docker", "exec", container, "pg_isready", "-U", "lilimi", "-d", "lilimi"],
                stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=False,
            )
            if probe.returncode == 0:
                break
            time.sleep(1)
        else:
            raise RuntimeError("Temporary restore database did not start")
        run(["docker", "exec", container, "pg_restore", "-U", "lilimi", "-d", "lilimi",
             "--exit-on-error", "--single-transaction", "/backup/database.dump"])

        with tarfile.open(directory / "media.tar.gz", "r:gz") as archive:
            archive.extractall(restored_media, filter="data")

        query = """
SELECT storage_key, byte_size, sha256 FROM digital_product_packages;
"""
        def rows(sql):
            result = run(["docker", "exec", container, "psql", "-XAt", "-F", "\t",
                          "-U", "lilimi", "-d", "lilimi", "-v", "ON_ERROR_STOP=1", "-c", sql])
            return [line.split("\t") for line in result.decode("utf-8").splitlines()]

        def restored_file(relative):
            path = (restored_media / relative).resolve()
            if not path.is_relative_to(restored_media.resolve()) or not path.is_file():
                raise RuntimeError("A database record references a missing restored file")
            return path

        packages = rows(query)
        for key, byte_size, digest in packages:
            path = restored_file(Path("private/digital-packages") / key)
            if path.stat().st_size != int(byte_size):
                raise RuntimeError("Restored ZIP size differs from its database record")
            with path.open("rb") as source:
                if hashlib.file_digest(source, "sha256").hexdigest() != digest:
                    raise RuntimeError("Restored ZIP hash differs from its database record")

        image_rows = rows("SELECT storage_key, byte_size FROM product_images")
        attachment_rows = rows("SELECT storage_key, byte_size FROM inquiry_attachments")
        for records, prefix in (
            (image_rows, Path(".")),
            (attachment_rows, Path("private/inquiry-attachments")),
        ):
            for key, byte_size in records:
                if restored_file(prefix / key).stat().st_size != int(byte_size):
                    raise RuntimeError("Restored media size differs from its database record")

        missing = rows("""
SELECT count(*) FROM order_items item
LEFT JOIN digital_product_packages package ON package.id = item.digital_package_id
WHERE item.digital_package_id IS NOT NULL AND package.id IS NULL
""")
        if missing != [["0"]]:
            raise RuntimeError("A restored order references a missing digital package")
        migration = rows("SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1")
        report = {
            "databaseRestored": True, "mediaRestored": True, "zipHashesVerified": len(packages),
            "productImagesVerified": len(image_rows), "inquiryAttachmentsVerified": len(attachment_rows),
            "orderPackageReferencesVerified": True, "schemaVersion": migration[0][0],
        }
        (directory / "restore-check.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
        print("RESTORE CHECK PASSED " + json.dumps(report), flush=True)
    finally:
        if created_container:
            run(["docker", "rm", "--force", container])
        if created_volume:
            run(["docker", "volume", "rm", volume])
        target = restored_media.resolve()
        if target.parent != directory or not target.name.startswith("restore-check-media-"):
            raise RuntimeError("Unexpected restore-check cleanup directory")
        shutil.rmtree(target)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    subcommands = parser.add_subparsers(dest="command", required=True)
    create = subcommands.add_parser("create", help="Create and download a consistent server backup")
    create.add_argument("--host", required=True, help="SSH user@host already present in known_hosts")
    create.add_argument("--identity", type=Path, default=Path.home() / ".ssh/lilimi-actions")
    create.add_argument("--destination", type=Path, default=Path("D:/angelika/kopie-zapasowe/Lilimi"))
    create.add_argument("--remote-directory", default="/home/ubuntu/lilimi")
    create.add_argument("--check", action="store_true", help="Also test database and media restoration")
    check = subcommands.add_parser("check", help="Restore an existing backup into a temporary local database")
    check.add_argument("directory", type=Path)
    args = parser.parse_args()
    if args.command == "create":
        create_backup(args)
    else:
        check_backup(args.directory)


if __name__ == "__main__":
    main()
