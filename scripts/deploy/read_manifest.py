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
        if deployment:
            if manifest_path is not None:
                raise SystemExit(
                    f"multiple manifests declare deployment config: '{manifest_path}' and '{path}'"
                )
            manifest_path = path
            doc = candidate

    if manifest_path is None or doc is None:
        raise SystemExit("exactly one manifest must include a deployment block")

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

    generic_scaling = generic_service.get("scaling", {})
    worker_scaling = worker_service.get("scaling", {})

    generic_min = int(generic_scaling.get("minCapacity", 1))
    generic_max = int(generic_scaling.get("maxCapacity", 5))
    worker_min = int(worker_scaling.get("minCapacity", 1))
    worker_max = int(worker_scaling.get("maxCapacity", 5))

    if generic_min < 0 or worker_min < 0:
        raise SystemExit("minCapacity must be >= 0")
    if generic_max < generic_min or worker_max < worker_min:
        raise SystemExit("maxCapacity must be >= minCapacity")

    with open(os.environ["GITHUB_ENV"], "a", encoding="utf-8") as out:
        out.write(f"DEPLOY_STAGE={manifest_stage}\n")
        out.write(f"DEPLOY_OPERATION={deploy_operation}\n")
        out.write(f"RELEASE_NAME={release_name}\n")
        out.write(f"GENERIC_REPOSITORY={generic_service['image']}\n")
        out.write(f"WORKER_REPOSITORY={worker_service['image']}\n")
        out.write(f"GENERIC_IMAGE={ecr_registry}/{generic_service['image']}:{generic_service['tag']}\n")
        out.write(f"WORKER_IMAGE={ecr_registry}/{worker_service['image']}:{worker_service['tag']}\n")
        out.write(f"GENERIC_TAG={generic_service['tag']}\n")
        out.write(f"WORKER_TAG={worker_service['tag']}\n")
        out.write(f"GENERIC_MIN_CAPACITY={generic_min}\n")
        out.write(f"GENERIC_MAX_CAPACITY={generic_max}\n")
        out.write(f"WORKER_MIN_CAPACITY={worker_min}\n")
        out.write(f"WORKER_MAX_CAPACITY={worker_max}\n")

    print(f"Loaded {manifest_path}")
    print(f"Release: {release_name}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
