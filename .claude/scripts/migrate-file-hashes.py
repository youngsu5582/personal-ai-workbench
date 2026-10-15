#!/usr/bin/env python3
"""로컬 DB의 파일 UUID와 저장된 입력 참조를 SHA-256으로 옮긴다. 기본은 dry-run."""
import argparse
import base64
import datetime
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import urllib.parse

ROOT = Path(__file__).resolve().parents[2]
HEX = re.compile(r"[0-9a-f]{64}")
TABLES = ("generated_files", "uploaded_files", "generation_jobs", "generation_job_tasks")


def command(args, *, env, input=None):
    result = subprocess.run(args, input=input, text=True, capture_output=True, env=env, timeout=180, cwd=ROOT)
    if result.returncode:
        # 명령 인자와 환경변수를 출력하지 않는다. 인증 정보는 로그에 남기지 않는다.
        raise RuntimeError(result.stderr[-1800:] or result.stdout[-1800:])
    return result.stdout.strip()


def literal(value):
    if value is None:
        return "NULL"
    return "'" + str(value).replace("'", "''") + "'"


def settings(path):
    values = {}
    for line in path.read_text().splitlines():
        if not line.strip() or line.lstrip().startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip().strip("'\"")
    for key in tuple(values):
        if key in os.environ:
            values[key] = os.environ[key]
    return values


def fingerprint_sql(table):
    return f"SELECT md5(COALESCE(jsonb_agg(to_jsonb(t) - 'sha256' ORDER BY id)::text, '[]')) FROM {table} t"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--env-file", type=Path, default=ROOT / ".env")
    parser.add_argument("--s3-endpoint")
    parser.add_argument("--pg-dump", help="서버와 같은 버전 또는 더 최신의 pg_dump 경로")
    parser.add_argument("--local-root", type=Path)
    parser.add_argument("--report-dir", type=Path)
    parser.add_argument("--skip-missing", action="store_true", help="원본 없는 파일과 그 파일을 참조하는 요청을 백업 후 제외")
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()
    config = settings(args.env_file)
    db = urllib.parse.urlsplit(config["DB_URL"].removeprefix("jdbc:"))
    if db.hostname not in ("localhost", "127.0.0.1", "::1"):
        raise ValueError("이 도구는 로컬 DB 전용이다")
    env = dict(os.environ, PGHOST=db.hostname, PGPORT=str(db.port or 5432),
               PGDATABASE=db.path.lstrip("/"), PGUSER=config["DB_USERNAME"],
               PGPASSWORD=config["DB_PASSWORD"], PGCONNECT_TIMEOUT="5")
    for name in ("psql", "java"):
        if not shutil.which(name):
            raise RuntimeError(f"{name}이 필요하다")
    def sql(query):
        return command(["psql", "-X", "-A", "-t", "-v", "ON_ERROR_STOP=1", "-c", query], env=env)
    def rows(query):
        return json.loads(sql(f"SELECT COALESCE(json_agg(x), '[]') FROM ({query}) x"))
    server_major = int(sql("SHOW server_version_num")) // 10000
    candidates = [args.pg_dump] if args.pg_dump else [
        f"/opt/homebrew/opt/postgresql@{server_major}/bin/pg_dump", shutil.which("pg_dump"),
        "/opt/homebrew/opt/libpq/bin/pg_dump", "/opt/homebrew/opt/libpq@18/bin/pg_dump",
    ]
    pg_dump = None
    for candidate in candidates:
        if not candidate or not Path(candidate).is_file():
            continue
        version = command([candidate, "--version"], env=env)
        major = int(re.search(r"\b(\d+)\.", version).group(1))
        if major >= server_major:
            pg_dump = candidate
            break
    if pg_dump is None:
        raise RuntimeError("DB와 같거나 최신인 pg_dump를 --pg-dump로 지정한다")
    columns = {r["table_name"]: set(r["columns"]) for r in rows(
        "SELECT table_name, array_agg(column_name) AS columns FROM information_schema.columns "
        "WHERE table_schema='public' AND table_name IN ('generated_files','uploaded_files') GROUP BY table_name"
    )}
    if not all("uuid" in columns.get(table, set()) for table in ("generated_files", "uploaded_files")):
        remaining = rows("SELECT id FROM generation_jobs WHERE option::text LIKE '%\"uuid\"%'")
        print(json.dumps({"already_converted": True, "legacy_job_references": len(remaining)}, ensure_ascii=False))
        if remaining:
            raise RuntimeError("UUID 열은 없지만 기존 Job 참조가 남아 있다")
        return
    fingerprints = {table: sql(fingerprint_sql(table)) for table in TABLES}
    generated = rows("SELECT f.*, j.owner_user_uuid FROM generated_files f JOIN generation_job_tasks t ON t.id=f.task_id "
                     "JOIN generation_jobs j ON j.id=t.job_id ORDER BY f.id")
    uploads = rows("SELECT * FROM uploaded_files ORDER BY id")
    jobs = rows("SELECT * FROM generation_jobs ORDER BY id")
    tasks = rows("SELECT * FROM generation_job_tasks ORDER BY id")
    before_data = json.loads(json.dumps({"generated": generated, "uploads": uploads, "jobs": jobs, "tasks": tasks}))
    provider = config.get("STORAGE_PROVIDER", "local")
    endpoint = args.s3_endpoint or config.get("STORAGE_S3_ENDPOINT", "")
    if provider == "s3":
        if urllib.parse.urlsplit(endpoint).hostname not in ("localhost", "127.0.0.1", "::1"):
            raise ValueError("이 도구는 로컬 S3 endpoint 전용이다")
        env.update(AWS_ACCESS_KEY_ID=config["STORAGE_S3_ACCESS_KEY"],
                   AWS_SECRET_ACCESS_KEY=config["STORAGE_S3_SECRET_KEY"],
                   AWS_DEFAULT_REGION=config.get("STORAGE_S3_REGION", "garage"), AWS_PAGER="")
        for key in ("AWS_SESSION_TOKEN", "AWS_SECURITY_TOKEN", "AWS_PROFILE"):
            env.pop(key, None)
        bucket = config["STORAGE_S3_BUCKET"]
        if not shutil.which("aws"):
            raise RuntimeError("aws CLI가 필요하다")
    local_root = (args.local_root or Path(config.get("STORAGE_LOCAL_ROOT", str(ROOT / "var/assets")))).resolve()

    mappings, missing, copies = {}, [], []
    generated_updates, upload_groups = [], {}
    with tempfile.TemporaryDirectory(prefix="file-hash-migration-") as directory:
        temporary = Path(directory)
        def read_object(key, tag):
            path = temporary / tag
            if provider == "s3":
                result = subprocess.run(
                    ["aws", "--endpoint-url", endpoint, "s3api", "get-object", "--bucket", bucket, "--key", key, str(path)],
                    env=env, capture_output=True, text=True, timeout=60)
                if result.returncode:
                    if "NoSuchKey" in result.stderr or "Not Found" in result.stderr or "404" in result.stderr:
                        return None
                    raise RuntimeError("객체 읽기 실패: " + result.stderr[-700:])
            else:
                source = (local_root / key).resolve()
                if not source.is_relative_to(local_root):
                    raise ValueError("보관소 밖의 키다")
                if not source.is_file():
                    return None
                shutil.copyfile(source, path)
            return path

        for kind, files in (("generated", generated), ("uploaded", uploads)):
            for row in files:
                owner, key = row["owner_user_uuid"], row["storage_key"]
                embedded = Path(key).name.split(".")[0]
                path = None
                if kind == "generated" and HEX.fullmatch(embedded):
                    digest = embedded
                else:
                    path = read_object(key, f"{kind}-{row['id']}")
                    if path is None:
                        missing.append({"type": kind, "id": row["id"], "uuid": row["uuid"], "storage_key": key})
                        continue
                    with path.open("rb") as content:
                        digest = hashlib.file_digest(content, "sha256").hexdigest()
                if row.get("sha256") and row["sha256"] != digest:
                    raise RuntimeError(f"기록된 해시와 내용이 다르다: {kind}/{row['id']}")
                mappings[(kind, owner, row["uuid"])] = digest
                if kind == "generated":
                    generated_updates.append((row["id"], digest))
                    continue
                mime = row["metadata"]["mimeType"]
                extension = mime.rsplit("/", 1)[-1]
                new_key = f"users/{owner}/uploads/{digest[:2]}/{digest[2:4]}/{digest}.{extension}"
                row.update(sha256=digest, new_storage_key=new_key)
                upload_groups.setdefault((owner, digest), []).append(row)
                if key != new_key:
                    copies.append((path, new_key, mime, digest))

        excluded_jobs = set()
        missing_ids = {(r["type"], r["uuid"]) for r in missing}
        # 삭제되는 요청의 결과를 참조하는 다른 요청도 함께 보관한다.
        while True:
            before = len(excluded_jobs)
            for job in jobs:
                sources = job["option"].get("sources", [])
                if any((s.get("type"), s.get("uuid")) in missing_ids for s in sources):
                    excluded_jobs.add(job["id"])
            excluded_tasks = {t["id"] for t in tasks if t["job_id"] in excluded_jobs}
            missing_ids.update(("generated", f["uuid"]) for f in generated if f["task_id"] in excluded_tasks)
            if len(excluded_jobs) == before:
                break
        updates = []
        for job in jobs:
            if job["id"] in excluded_jobs:
                continue
            option = json.loads(json.dumps(job["option"]))
            changed = False
            for source in option.get("sources", []):
                if "uuid" not in source:
                    continue
                lookup = (source["type"], job["owner_user_uuid"], source["uuid"])
                if lookup not in mappings:
                    raise RuntimeError(f"Job {job['id']}의 입력을 소유자와 함께 찾을 수 없다")
                source["sha256"] = mappings[lookup]
                del source["uuid"]
                changed = True
            if changed:
                updates.append((job["id"], option))
        report = {"generated": len(generated_updates),
                  "uploads": sum(map(len, upload_groups.values())), "unique_uploads": len(upload_groups),
                  "job_references": len(updates), "missing_files": missing,
                  "excluded_job_ids": sorted(excluded_jobs),
                  "mapping": [{"type": kind, "owner": owner, "uuid": uuid, "sha256": digest}
                              for (kind, owner, uuid), digest in mappings.items()]}
        print(json.dumps({k: v for k, v in report.items() if k != "mapping"}, ensure_ascii=False, indent=2))
        if missing and not args.skip_missing:
            raise RuntimeError("원본이 없는 파일이 있다. --skip-missing 선택 전 보고서를 확인한다")
        if not args.apply:
            return
        destination = args.report_dir or ROOT / "var" / ("file-hash-migration-" + datetime.datetime.now().strftime("%Y%m%d-%H%M%S"))
        destination.mkdir(parents=True, exist_ok=False)
        destination.chmod(0o700)
        (destination / "before.json").write_text(json.dumps(before_data, ensure_ascii=False, indent=2))
        (destination / "mapping.json").write_text(json.dumps(report, ensure_ascii=False, indent=2))
        dump = destination / "database-before.dump"
        with dump.open("wb") as out:
            result = subprocess.run([pg_dump, "-Fc", "--no-owner", "--no-acl"], env=env, stdout=out, stderr=subprocess.PIPE)
        if result.returncode:
            raise RuntimeError("DB 백업 실패: " + result.stderr.decode()[-500:])
        classpath = env.get("FILE_HASH_MIGRATION_CLASSPATH")
        if not classpath:
            cp = command([str(ROOT / "gradlew"), "-q", "fileHashMigrationClasspath"], env=env)
            classpath = next(line for line in reversed(cp.splitlines()) if ".jar" in line)
        flyway_env = dict(env, DB_URL=config["DB_URL"], DB_USERNAME=config["DB_USERNAME"], DB_PASSWORD=config["DB_PASSWORD"])
        def migrate(target):
            return command(["java", "--class-path", classpath, str(ROOT / ".claude/scripts/FlywayMigrate.java"),
                            str(ROOT / "src/main/resources/db/migration"), target], env=flyway_env)
        migrate("8")
        for path, key, mime, digest in copies:
            if provider == "s3":
                command(["aws", "--endpoint-url", endpoint, "s3api", "put-object", "--bucket", bucket, "--key", key,
                         "--body", str(path), "--content-type", mime, "--checksum-algorithm", "SHA256",
                         "--checksum-sha256", base64.b64encode(bytes.fromhex(digest)).decode()], env=env)
            else:
                target = local_root / key
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(path, target)
        statements = ["BEGIN;", "LOCK TABLE generated_files, uploaded_files, generation_jobs, generation_job_tasks IN ACCESS EXCLUSIVE MODE;"]
        for table, expected in fingerprints.items():
            statements.append(f"DO $$ BEGIN IF ({fingerprint_sql(table)}) <> {literal(expected)} "
                              "THEN RAISE EXCEPTION '변환 전에 데이터가 바뀌었다'; END IF; END $$;")
        if excluded_jobs:
            identifiers = ",".join(map(str, sorted(excluded_jobs)))
            statements += [
                f"DELETE FROM generated_files WHERE task_id IN (SELECT id FROM generation_job_tasks WHERE job_id IN ({identifiers}));",
                f"DELETE FROM generation_job_tasks WHERE job_id IN ({identifiers});",
                f"DELETE FROM generation_jobs WHERE id IN ({identifiers});",
            ]
        for item in missing:
            table = "uploaded_files" if item["type"] == "uploaded" else "generated_files"
            statements.append(f"DELETE FROM {table} WHERE id={item['id']};")
        for identifier, digest in generated_updates:
            statements.append(f"UPDATE generated_files SET sha256={literal(digest)} WHERE id={identifier};")
        for group in upload_groups.values():
            group.sort(key=lambda r: ({"READY": 0, "PENDING": 1, "REJECTED": 2}[r["status"]], r["id"]))
            kept, *duplicates = group
            for duplicate in duplicates:
                statements.append(f"DELETE FROM uploaded_files WHERE id={duplicate['id']};")
            statements.append(f"UPDATE uploaded_files SET sha256={literal(kept['sha256'])}, "
                              f"storage_key={literal(kept['new_storage_key'])} WHERE id={kept['id']};")
        for identifier, option in updates:
            statements.append(f"UPDATE generation_jobs SET option={literal(json.dumps(option))}::jsonb WHERE id={identifier};")
        statements.append("COMMIT;")
        plan = "\n".join(statements)
        (destination / "conversion.sql").write_text(plan)
        command(["psql", "-X", "-v", "ON_ERROR_STOP=1"], env=env, input=plan)
        migrate("9")
        verification = rows("SELECT 'generated' AS type, count(*) AS count FROM generated_files WHERE sha256 IS NULL "
                            "UNION ALL SELECT 'uploaded', count(*) FROM uploaded_files WHERE sha256 IS NULL "
                            "UNION ALL SELECT 'legacy_sources', count(*) FROM generation_jobs WHERE option::text LIKE '%\"uuid\"%'")
        if any(item["count"] for item in verification):
            raise RuntimeError("변환 후 검증 실패")
        (destination / "after.json").write_text(json.dumps(verification, indent=2))
        print(f"변환 완료 · 백업/매핑/SQL/검증: {destination}")


if __name__ == "__main__":
    main()
