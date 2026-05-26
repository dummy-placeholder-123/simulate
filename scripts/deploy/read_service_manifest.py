#!/usr/bin/env python3
import os
import re
import sys
from pathlib import Path

import yaml


TAG_PATTERN = re.compile(r"^[A-Za-z0-9._-]+$")


def main() -> int:
    if len(sys.argv) != 2:
        raise SystemExit("usage: read_service_manifest.py <manifest-path>")

    manifest_path = Path(sys.argv[1])
    if not manifest_path.is_file():
        raise SystemExit(f"manifest not found: {manifest_path}")

    with open(manifest_path, "r", encoding="utf-8") as f:
        doc = yaml.safe_load(f) or {}

    manifest_stage = (doc.get("environment") or "").strip()
    if not manifest_stage:
        raise SystemExit("environment is required in the manifest")

    expected_stage = manifest_path.parent.name
    if manifest_stage != expected_stage:
        raise SystemExit(
            f"manifest environment '{manifest_stage}' does not match folder '{expected_stage}'"
        )

    release_name = (doc.get("release-name") or "").strip()
    if not release_name:
        raise SystemExit("release-name is required in the manifest")

    deployment = doc.get("deployment") or {}
    deploy_operation = (deployment.get("operation") or "deploy").strip()
    if deploy_operation not in {"deploy", "push-only"}:
        raise SystemExit("deployment.operation must be 'deploy' or 'push-only'")

    tag = (doc.get("tag") or "").strip()
    if not TAG_PATTERN.match(tag):
        raise SystemExit(f"tag '{tag}' contains invalid characters")

    with open(os.environ["GITHUB_ENV"], "a", encoding="utf-8") as out:
        out.write(f"DEPLOY_STAGE={manifest_stage}\n")
        out.write(f"DEPLOY_OPERATION={deploy_operation}\n")
        out.write(f"RELEASE_NAME={release_name}\n")
        out.write(f"DEPLOY_TAG={tag}\n")
    print(f"Loaded {manifest_path}")
    print(f"Release: {release_name}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
