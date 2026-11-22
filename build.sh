#!/usr/bin/env bash
set -euo pipefail

if ! command -v mvn >/dev/null 2>&1; then
    echo "Maven is required to build Morning Athkar." >&2
    exit 1
fi

mvn clean package
