#!/bin/bash
# Restarts the PoC Fabric server and waits for it to finish starting; prints our log lines and errors.
cd "$(dirname "$0")/.."
since=$(date -u +%Y-%m-%dT%H:%M:%SZ)
docker compose restart fabric >/dev/null 2>&1
timeout 900 bash -c "until docker logs --since $since clpoc-fabric 2>&1 | grep -qE 'Done \(|Failed to start|OutOfMemory'; do sleep 5; done"
docker logs --since $since clpoc-fabric 2>&1 | grep -E '\[cobblemonlink\]|Done \(|Failed to start|OutOfMemory|Exception' | grep -v '^\s*at ' | head -${1:-15}
