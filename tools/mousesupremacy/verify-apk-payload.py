"""Compare all ZIP entries of two separately verified v2-only APKs."""

import argparse
import hashlib
import json
import re
import zipfile


def entries(path, source):
    with zipfile.ZipFile(path) as apk:
        entries_by_name = {entry.filename: entry for entry in apk.infolist()}
        if len(entries_by_name) != len(apk.infolist()):
            raise ValueError("Duplicate APK entry names")
        required = {"assets/source-commit.txt", "AndroidManifest.xml", "classes.dex"}
        if not required <= entries_by_name.keys():
            raise ValueError("Missing essential APK payload")
        if apk.read("assets/source-commit.txt").decode("ascii").strip() != source:
            raise ValueError("Unexpected embedded source commit")
        abis = {name.split("/")[1] for name in entries_by_name
                if re.fullmatch(r"lib/[^/]+/[^/]+\.so", name)}
        if abis != {"arm64-v8a", "x86_64"}:
            raise ValueError("Unexpected native ABI set")

        manifest = {}
        for name, entry in entries_by_name.items():
            with apk.open(entry) as stream:
                manifest[name] = [entry.file_size, entry.compress_type,
                                  hashlib.file_digest(stream, "sha256").hexdigest()]
        return manifest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("original")
    parser.add_argument("derived")
    parser.add_argument("source")
    args = parser.parse_args()
    if not re.fullmatch("[0-9a-f]{40}", args.source):
        parser.error("Pass the full lowercase source commit")
    try:
        first = entries(args.original, args.source)
        second = entries(args.derived, args.source)
    except (OSError, ValueError, zipfile.BadZipFile) as exc:
        raise SystemExit(f"APK payload verification failed: {exc}") from exc
    if first != second:
        raise SystemExit("Original and derived APK entry manifests differ")
    canonical = json.dumps(first, sort_keys=True, separators=(",", ":")).encode("utf-8")
    print(f"Equivalent APK ZIP entries: {len(first)}")
    print(f"Entry manifest SHA-256: {hashlib.sha256(canonical).hexdigest()}")


if __name__ == "__main__":
    main()
