#!/bin/bash
# ponytail: bm spawn is player-only, so the bot types it; each spawn is verified by husk count and retried
S="$(cd "$(dirname "$0")" && pwd)"
# needs python-xlib and the bot client running on Xvfb :99
P="${PY:-python3} -I $S/xkey.py"
C=${C:-mc-spike}; R="docker exec $C rcon-cli"
count() { $R "execute if entity @e[type=husk]" | grep -oE '[0-9]+$' || echo 0; }
measure() {
  $R "tp PDTest 25 -50 25 0 90" >/dev/null
  sleep 60
  cpu=$(for i in 1 2 3 4 5; do docker stats --no-stream --format "{{.CPUPerc}}" $C; sleep 1; done | tr -d % | awk '{s+=$1} END {printf "%.0f", s/NR}')
  t=$($R "tick query" | sed 's/\x1b\[[0-9;]*m//g' | grep -oE 'Average time per tick: [0-9.]+ms|P50: [0-9.]+ms P95: [0-9.]+ms P99: [0-9.]+ms' | tr '\n' ' ')
  echo "N=$(count) | $t| cpu ${cpu}%"
}
$R "kill @e[type=husk]" >/dev/null; $R "weather clear" >/dev/null; $R "gamerule spawn_mobs false" >/dev/null
measure
for target in 25 50 100; do
  while [ "$(count)" -lt $target ]; do
    n=$(count); x=$(( 10 + (n % 10) * 3 )); z=$(( 10 + (n / 10) * 3 ))
    $R "tp PDTest $x -60 $z" >/dev/null; sleep 0.4; $P "/bm spawn pikachu"; sleep 1.2
    $R "execute as @e[type=husk] run data merge entity @s {PersistenceRequired:1b}" >/dev/null
  done
  measure
done
