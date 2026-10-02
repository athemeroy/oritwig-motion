#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Verify final hashes, reconstruct pinned originals, and detect algorithm edits.

Offline and independent of apply-distribution-notices.py. Requires the standard
patch utility. Reconstructed originals exist only in a temporary directory.
"""
from pathlib import Path
import hashlib
import json
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]


def digest(data):
    return hashlib.sha256(data).hexdigest()


def comment_end(data):
    index = 3 if data.startswith(b"\xef\xbb\xbf") else 0
    while True:
        while index < len(data) and data[index:index + 1].isspace():
            index += 1
        if data[index:index + 2] == b"/*":
            index = data.index(b"*/", index + 2) + 2
        elif data[index:index + 2] == b"//":
            index = data.index(b"\n", index + 2) + 1
        else:
            return index


def main():
    original = json.loads((ROOT / "provenance/upstream-files.json").read_text())
    distribution = json.loads((ROOT / "provenance/distribution-files.json").read_text())
    integration = json.loads((ROOT / "provenance/integration-only-diff.json").read_text())
    final = {entry["path"]: entry for entry in distribution["files"]}
    allowed = {entry["path"] for entry in integration["files"]}
    assert allowed == {"vendor/rlottie/src/lottie/lottieparser.cpp",
                       "vendor/rlottie/src/vector/vraster.cpp"}
    assert set(final) == {entry["path"] for entry in original["files"]}
    assert len(final) == 115
    unchanged, notice_changed, includes_removed = 0, 0, 0
    with tempfile.TemporaryDirectory(prefix="motion-provenance-check-") as temp:
        stage = Path(temp)
        for path, entry in final.items():
            current = (ROOT / path).read_bytes()
            assert digest(current) == entry["distribution_sha256"], path
            target = stage / path
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(current)
        subprocess.run(["patch", "--batch", "--reverse", "--fuzz=0", "-p1",
                        "-d", temp, "-i", str(ROOT / "provenance/license-only.patch")],
                       check=True, stdout=subprocess.PIPE)
        for entry in original["files"]:
            path = entry["path"]
            reconstructed = (stage / path).read_bytes()
            current = (ROOT / path).read_bytes()
            if path in allowed:
                assert b"tgnet/FileLog.h" not in reconstructed, path
                previous = b"#include <sstream>\n" if path.endswith("lottieparser.cpp") else b"#include <memory>\n"
                assert reconstructed.count(previous) == 1, path
                reconstructed = reconstructed.replace(previous, previous + b"#include <tgnet/FileLog.h>\n", 1)
                includes_removed += 1
            blob = hashlib.sha1(b"blob " + str(len(reconstructed)).encode() + b"\0" + reconstructed).hexdigest()
            assert blob == entry["git_blob"], "Original Git blob mismatch: " + path
            assert digest(reconstructed) == entry["sha256"], path
            if reconstructed == current:
                unchanged += 1
                continue
            body = reconstructed[comment_end(reconstructed):]
            if path in allowed:
                body = body.replace(b"#include <tgnet/FileLog.h>\n", b"")
            assert body == current[comment_end(current):], "Non-comment algorithm edit: " + path
            for line in reconstructed.splitlines():
                if b"copyright" in line.lower():
                    assert line in current.splitlines(), "Copyright notice lost: " + path
            if any("section 3 conversion" in reason for reason in final[path]["changes"]):
                assert b"Lesser General Public" not in current, path
                assert b"GNU Lesser\n" not in current, path
            notice_changed += 1
    license_manifest = ROOT / "provenance/licenses/source-manifest.json"
    if license_manifest.exists():
        for entry in json.loads(license_manifest.read_text())["files"]:
            assert digest((ROOT / entry["path"]).read_bytes()) == entry["sha256"], entry["path"]
    print(f"PASS: 115 exact original blobs reconstructed; {unchanged} unchanged files; "
          f"{notice_changed} leading-comment edits; {includes_removed} approved include deletions; "
          "no algorithm edits or lost copyright lines")


if __name__ == "__main__":
    main()
