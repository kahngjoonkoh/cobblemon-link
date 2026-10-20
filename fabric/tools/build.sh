#!/bin/bash
# Builds the Fabric mod (fabric/mod) with Gradle in Docker, runs its tests, and puts the jar in fabric/mods-extra.
set -eo pipefail
cd "$(dirname "$0")/../mod"
mkdir -p ~/.cache/cl-fabric/gradle
docker run --rm -u "$(id -u):$(id -g)" -e GRADLE_USER_HOME=/g -e HOME=/tmp -v ~/.cache/cl-fabric/gradle:/g -v "$PWD":/w -w /w \
    gradle:8.12-jdk21 gradle -q --no-daemon build "$@" 2>&1 | { grep -v '^warning\|^Note:' || true; }
rm -f ../mods-extra/cobblemon-link-[0-9]*.jar
cp build/libs/cobblemon-link-*[0-9].jar ../mods-extra/
ls ../mods-extra/cobblemon-link-*.jar
