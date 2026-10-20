#!/bin/bash
# Builds the mod, copies it and the compose file to toby (~/clpoc), restarts the Fabric server.
set -e
cd "$(dirname "$0")"
./build.sh
rsync -a --delete ../docker-compose.yml ../mods-extra toby:clpoc/fabric/
ssh toby 'rm -f ~/clpoc/data/fabric/mods/cobblemon-link-fabric-*.jar'
./restart.sh "$@"
