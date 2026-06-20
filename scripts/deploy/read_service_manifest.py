#!/usr/bin/env python3
import os
import re
import subprocess
import sys
from pathlib import Path


TAG_PATTERN = re.compile(r"^[A-Za-z0-9._-]+$")
ZERO_SHA = "0000000000000000000000000000000000000000"
KEY_VALUE_PATTERN = re.compile(r"^([A-Za-z0-9._-]+):(?:\s*(.*))?$")


def parse_manifest(text):
    doc = {}

    for raw_line in text.splitlines():
        if raw_line.startswith((" ", "\t")):
            continue

        line = raw_line.split("#", 1)[0].strip()
        if not line:
            continue

        match = KEY_VALUE_PATTERN.match(line)
        if not match:
            raise SystemExit(f"unsupported manifest line: {raw_line}")

        key, value = match.groups()
        doc[key] = (value or "").strip().strip("\"'")

    return doc


def load_manifest(path):
    with open(path, "r", encoding="utf-8") as f:
        return parse_manifest(f.read())


def load_manifest_at_ref(ref, path):
    if not ref or ref == ZERO_SHA:
        return None

    try:
        result = subprocess.run(
            ["git", "show", f"{ref}:{path.as_posix()}"],
            check=True,
            text=True,
            capture_output=True,
        )
    except subprocess.CalledProcessError:
        return None

    return parse_manifest(result.stdout)


def manifest_tag(doc):
    return (doc.get("tag") or "").strip()


def main() -> int:
    if len(sys.argv) != 2:
        raise SystemExit("usage: read_service_manifest.py <manifest-path>")

    manifest_path = Path(sys.argv[1])
    if not manifest_path.is_file():
        raise SystemExit(f"manifest not found: {manifest_path}")

    doc = load_manifest(manifest_path)

    if "deployment" in doc:
        raise SystemExit("deployment block is no longer supported; deploy manifests are deployment-only")

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

    tag = manifest_tag(doc)
    if not TAG_PATTERN.match(tag):
        raise SystemExit(f"tag '{tag}' contains invalid characters")

    previous_doc = load_manifest_at_ref(os.environ.get("BEFORE_SHA", ""), manifest_path)
    previous_tag = manifest_tag(previous_doc) if previous_doc else None
    tag_changed = previous_tag != tag

    with open(os.environ["GITHUB_ENV"], "a", encoding="utf-8") as out:
        out.write(f"DEPLOY_STAGE={manifest_stage}\n")
        out.write(f"RELEASE_NAME={release_name}\n")
        out.write(f"DEPLOY_TAG={tag}\n")
        out.write(f"DEPLOY_TAG_CHANGED={str(tag_changed).lower()}\n")
    print(f"Loaded {manifest_path}")
    print(f"Release: {release_name}")
    print(f"Tag changed: {str(tag_changed).lower()}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
