#!/bin/bash
# ponytail: same grid, same 60s settle, same viewer position as bench_plugin.sh; only the spawn call differs
C=${C:-mc-cobble}; R="docker exec $C rcon-cli"
count() { $R "execute if entity @e[type=cobblemon:pokemon]" | grep -oE '[0-9]+$' || echo 0; }
measure() {
  $R "tp PDTest 25 -50 25 0 90" >/dev/null
  sleep 60
  cpu=$(for i in 1 2 3 4 5; do docker stats --no-stream --format "{{.CPUPerc}}" $C; sleep 1; done | tr -d % | awk '{s+=$1} END {printf "%.0f", s/NR}')
  t=$($R "tick query" | sed 's/\x1b\[[0-9;]*m//g' | grep -oE 'Average time per tick: [0-9.]+ms|P50: [0-9.]+ms P95: [0-9.]+ms P99: [0-9.]+ms' | tr '\n' ' ')
  echo "N=$(count) | $t| cpu ${cpu}%"
}
$R "kill @e[type=cobblemon:pokemon]" >/dev/null; $R "weather clear" >/dev/null; $R "gamerule doMobSpawning false" >/dev/null
measure
for target in 25 50 100; do
  while [ "$(count)" -lt $target ]; do
    n=$(count); x=$(( 10 + (n % 10) * 3 )); z=$(( 10 + (n / 10) * 3 ))
    $R "pokespawnat $x -60 $z pikachu" >/dev/null
  done
  measure
done
