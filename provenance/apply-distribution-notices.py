#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Reproduce the documented notice selection and two dependency-only deletions.

Run after fetching the exact original files. This script is offline. It changes
only leading comment notices plus the two explicitly listed unused includes.
It refuses any unexpected original or previously transformed bytes.
"""
from pathlib import Path
import difflib
import hashlib
import json

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "provenance/upstream-files.json"
LEDGER = ROOT / "provenance/distribution-files.json"
INTEGRATION = {
    "vendor/rlottie/src/lottie/lottieparser.cpp",
    "vendor/rlottie/src/vector/vraster.cpp",
}
DATE = "2026-10-01"


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def git_blob(data):
    return hashlib.sha1(b"blob " + str(len(data)).encode() + b"\0" + data).hexdigest()


def transform(path, original):
    text = original.decode("utf-8")
    reasons = []
    if path in INTEGRATION:
        line = "#include <tgnet/FileLog.h>\n"
        if text.count(line) != 1:
            raise ValueError("Expected exactly one unused logging include: " + path)
        text = text.replace(line, "")
        reasons.append("integration: remove unused tgnet/FileLog.h include")
    if not path.endswith((".cpp", ".h", ".S")):
        return text.encode("utf-8"), reasons
    first_directive = text.find("#")
    if first_directive < 0:
        return text.encode("utf-8"), reasons
    prefix, body = text[:first_directive], text[first_directive:]
    if "Samsung Electronics" in prefix and "Lesser General Public" in prefix:
        prefix = prefix.replace("Lesser General Public", "General Public")
        prefix = prefix.replace("GNU Lesser\n** General Public", "GNU\n** General Public")
        prefix = prefix.replace("version 2.1 of the License", "version 3 of the License")
        prefix = prefix.replace("License version 2.1", "License version 3")
        prefix = prefix.replace("$QT_BEGIN_LICENSE:LGPL$", "$QT_BEGIN_LICENSE:GPL$")
        prefix = prefix.replace("LICENSE.LGPL", "COPYING.GPL")
        prefix = prefix.replace("version 2.1 requirements", "version 3 requirements")
        prefix = prefix.replace("http://www.gnu.org/licenses/old-licenses/lgpl-2.1.html",
                                "https://www.gnu.org/licenses/gpl-3.0.html")
        prefix += ("/* Oritwig distribution notice, " + DATE + ".\n"
                   " * GNU license references above were converted to GPLv3 under\n"
                   " * LGPL-2.1 section 3. Copyrights and algorithm bodies are unchanged.\n"
                   " * See NOTICE.txt and provenance/license-only.patch.\n")
        if "Nokia Corporation" in prefix:
            prefix += (" * The historical preview-license branch and named Nokia Qt LGPL\n"
                       " * Exception 1.1 are retained for provenance, not elected here.\n"
                       " * The alternative GNU grant above is the selected GPLv3 grant.\n")
        prefix += " */\n\n"
        reasons.append("license: LGPL-2.1 section 3 conversion to GPLv3")
    elif path.endswith("/vinterpolator.cpp"):
        prefix += ("/* Oritwig distribution notice, " + DATE + ".\n"
                   " * This distribution selects GPLv3 under the original GPL version 2\n"
                   " * or later alternative above. The original tri-license notice and\n"
                   " * Brian Birtles attribution are retained; no algorithm is changed.\n"
                   " */\n\n")
        reasons.append("license: select GPLv3 under existing GPL-2.0-or-later option")
    elif path.endswith("/freetype/v_ft_math.cpp"):
        prefix += ("/* Additional upstream attribution retained for this distribution:\n"
                   " * Copyright 2018 The Abseil Authors.\n"
                   " * The fallback bit-count fragment below is adapted from bits.h at\n"
                   " * Chromium commit 59afd8336009c9d97c22854c52e0382b62b3aa5e,\n"
                   " * third_party/abseil-cpp/absl/base/internal/bits.h (Apache-2.0).\n"
                   " * See provenance/licenses/APACHE-2.0.txt and NOTICE.txt.\n"
                   " * Attribution added " + DATE + "; no algorithm body changed.\n"
                   " */\n\n")
        reasons.append("notice: restore Abseil Apache-2.0 attribution")
    return (prefix + body).encode("utf-8"), reasons


def main():
    manifest = json.loads(MANIFEST.read_text())
    previous = {}
    if LEDGER.exists():
        previous = {f["path"]: f for f in json.loads(LEDGER.read_text())["files"]}
    records, patches = [], []
    for original_entry in manifest["files"]:
        path = original_entry["path"]
        target = ROOT / path
        before = target.read_bytes()
        if git_blob(before) != original_entry["git_blob"]:
            if path in previous and sha256(before) == previous[path]["distribution_sha256"]:
                records.append(previous[path])
                continue
            # The first application may follow the separately authorized include deletion.
            if path not in INTEGRATION:
                raise ValueError("Unexpected source bytes: " + path)
            audit = ROOT / "provenance/integration-only-diff.json"
            accepted = {e["path"]: e for e in json.loads(audit.read_text())["files"]}
            if sha256(before) != accepted[path]["after_integration_sha256"]:
                raise ValueError("Unexpected integration bytes: " + path)
            # Restore the include at its exact original adjacent sequence, then validate.
            if path.endswith("lottieparser.cpp"):
                before = before.replace(b"#include <sstream>\n", b"#include <sstream>\n#include <tgnet/FileLog.h>\n", 1)
            else:
                before = before.replace(b"#include <memory>\n", b"#include <memory>\n#include <tgnet/FileLog.h>\n", 1)
            if git_blob(before) != original_entry["git_blob"]:
                raise ValueError("Could not reconstruct exact original: " + path)
        after, reasons = transform(path, before)
        target.write_bytes(after)
        records.append({"path": path, "original_git_blob": original_entry["git_blob"],
                        "original_sha256": original_entry["sha256"],
                        "original_url": original_entry["url"],
                        "distribution_sha256": sha256(after), "changes": reasons})
        if after != before:
            # Exclude the separately recorded integration deletions from the license-only patch.
            integrated = before.replace(b"#include <tgnet/FileLog.h>\n", b"") if path in INTEGRATION else before
            patches.extend(difflib.unified_diff(integrated.decode("utf-8").splitlines(True),
                                               after.decode("utf-8").splitlines(True),
                                               fromfile="a/" + path, tofile="b/" + path))
    LEDGER.write_text(json.dumps({"upstream_pin": manifest["pin"],
        "distribution": "GPLv3 combined work; component notices retained",
        "date": DATE, "files": records}, indent=2) + "\n")
    if patches:
        (ROOT / "provenance/license-only.patch").write_text("".join(patches))
    print("Verified distribution files:", len(records))


if __name__ == "__main__":
    main()
