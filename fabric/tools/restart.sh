#!/bin/bash
# Restarts the Fabric test server on toby and waits for it to start; prints our log lines and errors.
since=$(date -u +%Y-%m-%dT%H:%M:%SZ)
ssh toby 'cd ~/clpoc/fabric && docker compose up -d fabric >/dev/null 2>&1 && docker compose restart fabric >/dev/null 2>&1'
timeout 900 bash -c "until ssh toby docker logs --since $since clpoc-fabric 2>&1 | grep -qE 'Done \(|Failed to start|OutOfMemory'; do sleep 5; done"
ssh toby docker logs --since $since clpoc-fabric 2>&1 | grep -E '\[cobblemonlink\]|Done \(|Failed to start|OutOfMemory|Exception' | grep -v '^\s*at ' | head -${1:-15}
