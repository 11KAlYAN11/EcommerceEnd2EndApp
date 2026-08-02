#!/usr/bin/env bash
# ShopEase microservices — verification script (Phase 16)
#
# Usage:
#   ./verify-services.sh status              show UP/down for every known service
#   ./verify-services.sh start [name|all]    start one service (or all), waits for health
#   ./verify-services.sh stop  [name|all]    stop one service (or all)
#   ./verify-services.sh test  [name|all]    just poll health, no (re)start
#   ./verify-services.sh flow                tier-2: login -> JWT -> use it cross-service
#
# Handles the two recurring problems by hand:
#   PORT ISSUES: a crashed/leftover run leaves java.exe holding a port. On
#     Windows/Git Bash, `kill <pid>` from a backgrounded `&` often does NOT
#     stop the real java.exe (it only signals the bash job wrapper). This
#     script finds the ACTUAL Windows PID bound to each port via `netstat`
#     and stops that with `taskkill //F //PID`, before starting anything.
#   JWT ISSUES: `flow` logs into user-service, then uses that token against
#     product-service. If that 2nd call doesn't return 201, the most likely
#     cause is jwt.secret mismatch between the two services' properties
#     files (they must be byte-for-byte identical, or share the same
#     JWT_SECRET env var) — the script says so directly.
#
# TO ADD A NEW SERVICE LATER (e.g. cart-service): add one line to the
# SERVICES array below. Nothing else in this script needs to change.

set -uo pipefail

# format: name:port:jar_path:health_path (health_path includes context-path)
SERVICES=(
  "notification:8081:notification-service/target/notification-service-0.0.1-SNAPSHOT.jar:/actuator/health"
  "user:8082:user-service/target/user-service-0.0.1-SNAPSHOT.jar:/api/actuator/health"
  "product:8083:product-service/target/product-service-0.0.1-SNAPSHOT.jar:/api/actuator/health"
)

LOG_DIR="logs"
mkdir -p "$LOG_DIR"

svc_field() { echo "$1" | cut -d: -f"$2"; }

find_by_name() {
  for s in "${SERVICES[@]}"; do
    if [ "$(svc_field "$s" 1)" = "$1" ]; then echo "$s"; return 0; fi
  done
  return 1
}

# Real Windows PID currently LISTENING on a TCP port.
port_pid() {
  local port=$1
  netstat -ano 2>/dev/null | grep -E "LISTENING" | grep ":$port " | awk '{print $NF}' | head -1
}

stop_port() {
  local port=$1
  local pid
  pid=$(port_pid "$port")
  if [ -n "${pid:-}" ]; then
    echo "  stopping PID $pid on port $port"
    taskkill //F //PID "$pid" >/dev/null 2>&1
  else
    echo "  nothing listening on $port"
  fi
}

wait_healthy() {
  local name=$1 port=$2 health=$3
  for i in $(seq 1 30); do
    code=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:$port$health" 2>/dev/null)
    if [ "$code" = "200" ]; then echo "[$name] UP (${i}s)"; return 0; fi
    sleep 1
  done
  echo "[$name] FAILED to come up in 30s — check $LOG_DIR/$name.log"
  tail -30 "$LOG_DIR/$name.log" 2>/dev/null
  return 1
}

start_one() {
  local s; s=$(find_by_name "$1") || { echo "unknown service: $1"; return 1; }
  local name port jar health
  name=$(svc_field "$s" 1); port=$(svc_field "$s" 2); jar=$(svc_field "$s" 3); health=$(svc_field "$s" 4)

  local existing; existing=$(port_pid "$port")
  if [ -n "${existing:-}" ]; then
    echo "[$name] port $port already in use (PID $existing) — stopping it first"
    stop_port "$port"
    sleep 1
  fi

  if [ ! -f "$jar" ]; then
    local moduledir="${jar%%/*}"
    echo "[$name] jar not found: $jar"
    echo "  build it first:  cd $moduledir && ../mvnw package -DskipTests"
    return 1
  fi

  echo "[$name] starting on :$port ..."
  nohup java -jar "$jar" > "$LOG_DIR/$name.log" 2>&1 &
  wait_healthy "$name" "$port" "$health"
}

start_all() { for s in "${SERVICES[@]}"; do start_one "$(svc_field "$s" 1)"; done; }
stop_all()  { for s in "${SERVICES[@]}"; do stop_port "$(svc_field "$s" 2)"; done; }

status() {
  printf "%-14s %-6s %-8s\n" "SERVICE" "PORT" "STATUS"
  for s in "${SERVICES[@]}"; do
    local name port health code st
    name=$(svc_field "$s" 1); port=$(svc_field "$s" 2); health=$(svc_field "$s" 4)
    code=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:$port$health" 2>/dev/null)
    if [ "$code" = "200" ]; then st="UP"; else st="down"; fi
    printf "%-14s %-6s %-8s\n" "$name" "$port" "$st"
  done
}

# Tier-2: real cross-service JWT flow (user-service issues, product-service trusts it)
flow_test() {
  echo "== cross-service JWT flow =="
  local token
  token=$(curl -s -X POST http://localhost:8082/api/auth/login \
    -H "Content-Type: application/json" \
    -d '{"email":"admin@test.com","password":"Admin@123"}' | grep -o '"token":"[^"]*"' | cut -d'"' -f4)

  if [ -z "$token" ]; then
    echo "FAIL: could not get a JWT from user-service. Is it up? -> ./verify-services.sh status"
    return 1
  fi
  echo "OK: got JWT from user-service (${token:0:20}...)"

  local code
  code=$(curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8083/api/categories \
    -H "Authorization: Bearer $token" -H "Content-Type: application/json" \
    -d '{"name":"SmokeTestCategory","description":"created by verify-services.sh"}')

  if [ "$code" = "201" ]; then
    echo "OK: product-service accepted the token and created a category (201)"
  else
    echo "FAIL: product-service returned $code instead of 201."
    echo "  Most likely cause: jwt.secret differs between user-service and product-service"
    echo "  application.properties (or their JWT_SECRET env vars) -- they must match exactly."
    return 1
  fi

  code=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8083/api/products)
  if [ "$code" = "200" ]; then
    echo "OK: public GET /products works with no token at all (200)"
  else
    echo "FAIL: public GET /products returned $code (expected 200 -- browsing should never need auth)"
  fi
}

usage() {
  cat <<EOF
Usage: ./verify-services.sh <command> [service]

  status              show UP/down for every known service
  start [name|all]    start one service (or all), waits for health
  stop  [name|all]    stop one service (or all)
  test  [name|all]    just poll health, no (re)start
  flow                tier-2: login on user-service, use the JWT on product-service

Known services: $(for s in "${SERVICES[@]}"; do printf "%s " "$(svc_field "$s" 1)"; done)

To add a new service later (e.g. cart-service), add one line to the
SERVICES array at the top of this file, for example:
  "cart:8084:cart-service/target/cart-service-0.0.1-SNAPSHOT.jar:/api/actuator/health"
EOF
}

case "${1:-}" in
  status) status ;;
  start)
    if [ "${2:-all}" = "all" ]; then start_all; else start_one "$2"; fi
    ;;
  stop)
    if [ "${2:-all}" = "all" ]; then
      stop_all
    else
      s=$(find_by_name "$2") && stop_port "$(svc_field "$s" 2)"
    fi
    ;;
  test)
    if [ "${2:-all}" = "all" ]; then
      for s in "${SERVICES[@]}"; do
        wait_healthy "$(svc_field "$s" 1)" "$(svc_field "$s" 2)" "$(svc_field "$s" 4)"
      done
    else
      s=$(find_by_name "$2") && wait_healthy "$(svc_field "$s" 1)" "$(svc_field "$s" 2)" "$(svc_field "$s" 4)"
    fi
    ;;
  flow) flow_test ;;
  *) usage ;;
esac
