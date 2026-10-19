#!/bin/bash
# Cobblemon 1.21.1 client "PocMod" (Fabric, mods in ~/.cache/cl-fabric-poc/modwork/mods) on Xvfb :98; waits until it joins
S=/tmp/claude-1000/-home-kkj-prod-mc-server/1057aea6-e740-40fa-a57d-afdbb626b1b7/scratchpad; P=~/.cache/cl-fabric-poc
ps -eo pid=,comm=,args= | awk '$2=="java" && (index($0,"pocwork")>0 || index($0,"modwork")>0) {print $1}' | xargs -r kill; sleep 3
since=$(date -u +%Y-%m-%dT%H:%M:%SZ)
(DISPLAY=:98 LIBGL_ALWAYS_SOFTWARE=1 nohup $S/venv/bin/portablemc --main-dir $P/mc --work-dir $P/modwork --output human start fabric:1.21.1 --jvm-args="-Xmx1500M" -u ${1:-PocMod} -s 127.0.0.1 -p ${2:-25570} --resolution 1280x720 > $P/modclient.log 2>&1 &)
timeout 600 bash -c "until docker logs --since $since clpoc-fabric 2>&1 | grep -q 'cobblemonlink\] ${1:-PocMod}'; do sleep 5; done"; sleep 20
docker logs --since $since clpoc-fabric 2>&1 | grep "cobblemonlink\] ${1:-PocMod}"
