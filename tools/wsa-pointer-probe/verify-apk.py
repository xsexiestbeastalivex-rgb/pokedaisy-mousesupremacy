"""Verify the packaged probe and write a sidecar for the Windows installer."""

import hashlib
import json
from pathlib import Path
import re
import sys
import zipfile

directory, commit = Path(sys.argv[1]), sys.argv[2]
package = "com.pokedaisy.mousesupremacy.probe"
activity = package + ".ProbeActivity"
label = "MouseSupremacy Pointer Probe"
apk = directory / "mousesupremacy-pointer-probe.apk"
badging = (directory / "apk-badging.txt").read_text()
signature = (directory / "apk-signature.txt").read_text()
manifest = (directory / "apk-manifest.txt").read_text()


def check(value, message):
    if not value:
        raise SystemExit(message)


for line in (
    f"package: name='{package}' versionCode='1' versionName='1.0'",
    f"launchable-activity: name='{activity}'",
    "sdkVersion:'26'",
    "targetSdkVersion:'34'",
    "Verified using v2 scheme (APK Signature Scheme v2): true",
    "Number of signers: 1",
):
    check(line in badging or line in signature, "Absent verification result: " + line)

check(re.search(rf'A: android:label\([^)]*\)="{re.escape(label)}"', manifest), "Incorrect application label")
components = re.findall(r"^\s*E: (activity|service|receiver|provider)(?:[\s(]|$)", manifest, re.M)
check(components == ["activity"], "Unexpected manifest components")
check("uses-permission" not in manifest, "Unwanted manifest permission")
for name, encoded in (
    ("debuggable", "0xffffffff"),
    ("exported", "0xffffffff"),
    ("allowBackup", "0x0"),
    ("resizeableActivity", "0xffffffff"),
):
    check(re.search(rf"A: android:{name}\(.*\)\s*=\s*\(type 0x12\){encoded}\b", manifest),
          f"Missing expected manifest value for {name}")

with zipfile.ZipFile(apk) as archive:
    entries = set(archive.namelist())
    check({"classes.dex", "assets/NOTICE", "assets/LICENSE"} <= entries, "Missing code or legal notices")
    check(not any(name.startswith("lib/") or name.endswith(".so") for name in entries),
          "Unexpected native library")
    check(archive.read("assets/source-commit.txt").decode().strip() == commit, "Source SHA mismatch")

certificate = re.search(r"^Signer #1 certificate SHA-256 digest: ([a-f0-9]{64})$", signature, re.M)
check(certificate is not None, "Missing signer digest")
metadata = {
    "schemaVersion": 1,
    "sourceCommit": commit,
    "apk": apk.name,
    "sha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
    "signerSha256": certificate[1],
    "signatureScheme": "v2",
    "package": package,
    "activity": activity,
    "label": label,
    "versionName": "1.0",
    "minSdk": 26,
    "targetSdk": 34,
}
(directory / "probe-build.json").write_text(json.dumps(metadata, indent=2) + "\n")
print(json.dumps(metadata, indent=2))
