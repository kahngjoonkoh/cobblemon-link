#!/bin/sh
# join.sh HOST PORT NAME [LANG]: (re)start the client logged in offline as NAME; LANG sets its language (e.g. ko_kr)
pkill java; pkill -f portablemc; sleep 2
W=/mc/${MCW:-work}; mkdir -p $W
[ -n "$4" ] && printf 'lang:%s\n' "$4" > $W/options.txt.lang && { grep -v '^lang:' $W/options.txt 2>/dev/null; cat $W/options.txt.lang; } > $W/o && mv $W/o $W/options.txt
nohup portablemc --main-dir /mc/main --work-dir $W --output human start 26.3 --jvm /usr/bin/java --jvm-args="-Xmx3G" \
  -u "$3" -s "$1" -p "$2" --resolution 1280x720 > $W/client.log 2>&1 &
