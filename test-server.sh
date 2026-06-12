#!/bin/bash
# Smoke-test the RemoteCompose server endpoints.
set -e

BASE="${1:-http://localhost:8080}"
VP="w=448&h=997&d=3.0"

check() {
    local label="$1" url="$2" expected="$3"
    local out
    out=$(curl -s -o /dev/null -w "%{http_code} %{size_download}" "$url")
    local code="${out% *}" size="${out#* }"
    if [ "$code" = "$expected" ]; then
        echo "✅ $label -> $code (${size}B)"
    else
        echo "❌ $label -> $code (expected $expected)"
        exit 1
    fi
}

check "health           " "$BASE/health" 200
check "users list       " "$BASE/ui/users?$VP&waves=2&favs=1,3" 200
check "user detail      " "$BASE/ui/users/1?$VP&fav=1" 200
check "unknown user 404 " "$BASE/ui/users/999?$VP" 404
echo "All good."
