#!/usr/bin/env python3
import os
import re
import sys
from pathlib import Path

import yaml


def main() -> int:
    ecr_registry = os.environ["ECR_REGISTRY"]
    manifest_path = None
    doc = None

    for path in sorted(Path("environments").glob("*.yml")):
        with open(path, "r", encoding="utf-8") as f:
            candidate = yaml.safe_load(f)
        deployment = candidate.get("deployment") or {}
        if deployment.get("selected") is True:
            if manifest_path is not None:
                raise SystemExit(
                    f"multiple manifests are selected for deployment: '{manifest_path}' and '{path}'"
                )
            manifest_path = path
            doc = candidate

    if manifest_path is None or doc is None:
        raise SystemExit("exactly one manifest must set deployment.selected: true")

    manifest_stage = (doc.get("environment") or "").strip()
    if not manifest_stage:
        raise SystemExit("environment is required in the manifest")

    release_name = (doc.get("release-name") or "").strip()
    if not release_name:
        raise SystemExit("release-name is required in the manifest")

    deployment = doc.get("deployment") or {}
    deploy_operation = (deployment.get("operation") or "deploy").strip()
    if deploy_operation not in {"deploy", "push-only"}:
        raise SystemExit("deployment.operation must be 'deploy' or 'push-only'")

    generic_service = doc["services"]["generic-service"]
    worker_service = doc["services"]["worker-service"]

    tag_pattern = re.compile(r"^[A-Za-z0-9._-]+$")
    for label, svc in (
        ("generic-service", generic_service),
        ("worker-service", worker_service),
    ):
        if not tag_pattern.match(svc["tag"]):
            raise SystemExit(f"{label} tag '{svc['tag']}' contains invalid characters")

    for label, svc in (
        ("generic-service", generic_service),
        ("worker-service", worker_service),
    ):
        if not (svc.get("repo") or "").strip():
            raise SystemExit(f"{label} repo is required")

    with open(os.environ["GITHUB_ENV"], "a", encoding="utf-8") as out:
        out.write(f"DEPLOY_STAGE={manifest_stage}\n")
        out.write(f"DEPLOY_OPERATION={deploy_operation}\n")
        out.write(f"RELEASE_NAME={release_name}\n")
        out.write(f"GENERIC_REPOSITORY={generic_service['repo']}\n")
        out.write(f"WORKER_REPOSITORY={worker_service['repo']}\n")
        out.write(f"GENERIC_IMAGE={ecr_registry}/{generic_service['repo']}:{generic_service['tag']}\n")
        out.write(f"WORKER_IMAGE={ecr_registry}/{worker_service['repo']}:{worker_service['tag']}\n")
        out.write(f"GENERIC_TAG={generic_service['tag']}\n")
        out.write(f"WORKER_TAG={worker_service['tag']}\n")

    print(f"Loaded {manifest_path}")
    print(f"Release: {release_name}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
