#!/bin/bash
# vanilla 1.21.1 client "PocJava" on Xvfb :98 (start Xvfb first); waits until it joins or fails
S=/tmp/claude-1000/-home-kkj-prod-mc-server/1057aea6-e740-40fa-a57d-afdbb626b1b7/scratchpad; P=~/.cache/cl-fabric-poc
ps -eo pid=,comm=,args= | awk '$2=="java" && (index($0,"pocwork")>0 || index($0,"modwork")>0) {print $1}' | xargs -r kill; sleep 3
(DISPLAY=:98 LIBGL_ALWAYS_SOFTWARE=1 nohup $S/venv/bin/portablemc --main-dir $P/mc --work-dir $P/pocwork --output human start 1.21.1 --jvm-args="-Xmx1200M" -u ${1:-PocJava} -s 127.0.0.1 -p ${2:-25570} --resolution 1280x720 > $P/client.log 2>&1 &)
timeout 400 bash -c "until grep -qE 'Client disconnected|Loaded [0-9]+ advancements|joined the game|Connecting to' $P/client.log; do sleep 3; done"; sleep 25
grep -E 'Client disconnected|Failed to|Connecting to' $P/client.log | tail -3
