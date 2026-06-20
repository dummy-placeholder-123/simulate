#!/usr/bin/env python3
import os
import re
import sys
from pathlib import Path


TAG_PATTERN = re.compile(r"^[A-Za-z0-9._-]+$")
KEY_VALUE_PATTERN = re.compile(r"^([A-Za-z0-9._-]+):(?:\s*(.*))?$")


def load_config(path):
    doc = {}

    with open(path, "r", encoding="utf-8") as f:
        for raw_line in f:
            if raw_line.startswith((" ", "\t")):
                continue

            line = raw_line.split("#", 1)[0].strip()
            if not line:
                continue

            match = KEY_VALUE_PATTERN.match(line)
            if not match:
                raise SystemExit(f"unsupported frontend release config line: {raw_line.rstrip()}")

            key, value = match.groups()
            doc[key] = (value or "").strip().strip("\"'")

    return doc


def main() -> int:
    if len(sys.argv) != 2:
        raise SystemExit("usage: read_frontend_release.py <config-path>")

    config_path = Path(sys.argv[1])
    if not config_path.is_file():
        raise SystemExit(f"frontend release config not found: {config_path}")

    doc = load_config(config_path)

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
