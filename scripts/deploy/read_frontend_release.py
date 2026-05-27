#!/usr/bin/env python3
import os
import re
import sys
from pathlib import Path

import yaml


TAG_PATTERN = re.compile(r"^[A-Za-z0-9._-]+$")


def main() -> int:
    if len(sys.argv) != 2:
        raise SystemExit("usage: read_frontend_release.py <config-path>")

    config_path = Path(sys.argv[1])
    if not config_path.is_file():
        raise SystemExit(f"frontend release config not found: {config_path}")

    with open(config_path, "r", encoding="utf-8") as f:
        doc = yaml.safe_load(f) or {}

    service_dir = (doc.get("service-dir") or "").strip()
    release_label = (doc.get("release-label") or config_path.stem).strip()
    stable_tag = (doc.get("stable-tag") or "latest").strip()
    artifact_prefix = (doc.get("artifact-prefix") or config_path.stem).strip()

    if not service_dir:
        raise SystemExit("service-dir is required in frontend release config")
    if not release_label:
        raise SystemExit("release-label must not be empty")
    if not TAG_PATTERN.match(stable_tag):
        raise SystemExit(f"stable-tag '{stable_tag}' contains invalid characters")
    if not artifact_prefix:
        raise SystemExit("artifact-prefix must not be empty")

    with open(os.environ["GITHUB_ENV"], "a", encoding="utf-8") as out:
        out.write(f"FRONTEND_SERVICE_DIR={service_dir}\n")
        out.write(f"FRONTEND_RELEASE_LABEL={release_label}\n")
        out.write(f"FRONTEND_STABLE_TAG={stable_tag}\n")
        out.write(f"FRONTEND_ARTIFACT_PREFIX={artifact_prefix}\n")

    print(f"Loaded {config_path}")
    print(f"Service dir: {service_dir}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
