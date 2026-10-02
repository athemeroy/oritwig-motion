#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
grep -q '^distributionSha256Sum=f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6$' gradle/wrapper/gradle-wrapper.properties
printf '%s  %s\n' '2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046' gradle/wrapper/gradle-wrapper.jar | sha256sum -c -
