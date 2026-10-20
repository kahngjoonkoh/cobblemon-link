# Test servers run on toby (Tailscale); source this. ponytail: host and paths fixed for this setup
export CLPOC_HOST=100.106.192.12
dk() { ssh toby docker "$@"; }                                    # docker on toby
R() { ssh toby "docker exec clpoc-fabric rcon-cli --password poc-only -- $(printf '%q' "$*")"; }  # a server command
