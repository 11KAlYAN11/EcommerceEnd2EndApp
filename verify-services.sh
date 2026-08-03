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
  "cart:8084:cart-service/target/cart-service-0.0.1-SNAPSHOT.jar:/api/actuator/health"
  "order:8085:order-service/target/order-service-0.0.1-SNAPSHOT.jar:/api/actuator/health"
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

  # Unique name every run -- Category.name has a DB unique constraint, so a
  # fixed literal name would 409 on the 2nd+ run against a real (persistent)
  # Postgres DB. This is NOT a JWT problem; a stale/duplicate name just looks
  # like one if you don't read the actual response body.
  local run_tag; run_tag=$(date +%s)
  local cat_resp code cat_id
  cat_resp=$(curl -s -X POST http://localhost:8083/api/categories \
    -H "Authorization: Bearer $token" -H "Content-Type: application/json" \
    -d "{\"name\":\"SmokeTestCategory-$run_tag\",\"description\":\"created by verify-services.sh\"}")
  cat_id=$(echo "$cat_resp" | grep -o '"id":[0-9]*' | head -1 | grep -o '[0-9]*')

  if [ -n "$cat_id" ]; then
    echo "OK: product-service accepted the token and created a category (id=$cat_id)"
  else
    echo "FAIL: product-service did not return a category id. Raw response:"
    echo "  $cat_resp"
    echo "  (401/403 here would mean a real JWT/secret problem -- a 409 'Data conflict'"
    echo "  usually just means the name already exists from a previous run.)"
    return 1
  fi

  code=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8083/api/products)
  if [ "$code" = "200" ]; then
    echo "OK: public GET /products works with no token at all (200)"
  else
    echo "FAIL: public GET /products returned $code (expected 200 -- browsing should never need auth)"
  fi

  # cart-service: only runs this step if it's actually up (older checkouts won't have it yet)
  if curl -s -o /dev/null -w "%{http_code}" http://localhost:8084/api/actuator/health 2>/dev/null | grep -q 200; then
    local prod_resp prod_id
    prod_resp=$(curl -s -X POST http://localhost:8083/api/products \
      -H "Authorization: Bearer $token" -H "Content-Type: application/json" \
      -d "{\"name\":\"SmokeTestProduct-$run_tag\",\"price\":9.99,\"stockQuantity\":5,\"categoryId\":$cat_id}")
    prod_id=$(echo "$prod_resp" | grep -o '"id":[0-9]*' | head -1 | grep -o '[0-9]*')

    if [ -z "$prod_id" ]; then
      echo "FAIL: could not create a product to add to cart. Raw response:"
      echo "  $prod_resp"
      return 1
    fi

    code=$(curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8084/api/cart/items \
      -H "Authorization: Bearer $token" -H "Content-Type: application/json" \
      -d "{\"productId\":$prod_id,\"quantity\":2}")

    if [ "$code" = "200" ]; then
      echo "OK: cart-service fetched the product from product-service and added it (200)"
    else
      echo "FAIL: cart-service returned $code adding product $prod_id."
      echo "  Most likely cause: product-service unreachable at services.product.base-url,"
      echo "  or the JWT wasn't trusted (same jwt.secret check as above applies here too)."
      return 1
    fi

    local cart_json
    cart_json=$(curl -s http://localhost:8084/api/cart -H "Authorization: Bearer $token")
    if echo "$cart_json" | grep -q "SmokeTestProduct-$run_tag"; then
      echo "OK: GET /cart shows the product name/price snapshot pulled from product-service"
    else
      echo "FAIL: cart contents don't show the expected product -- $cart_json"
    fi

    # order-service: only runs if it's up. Touches ALL FOUR other services in one call.
    if curl -s -o /dev/null -w "%{http_code}" http://localhost:8085/api/actuator/health 2>/dev/null | grep -q 200; then
      local stock_before order_resp order_id stock_after
      stock_before=$(curl -s http://localhost:8083/api/products/$prod_id | grep -o '"stockQuantity":[0-9]*' | grep -o '[0-9]*')

      order_resp=$(curl -s -X POST http://localhost:8085/api/orders \
        -H "Authorization: Bearer $token" -H "Content-Type: application/json" -d '{}')
      order_id=$(echo "$order_resp" | grep -o '"orderId":[0-9]*' | head -1 | grep -o '[0-9]*')

      if [ -z "$order_id" ]; then
        echo "FAIL: order-service did not return an orderId. Raw response:"
        echo "  $order_resp"
        return 1
      fi
      echo "OK: order-service placed an order (id=$order_id) -- pulled cart, fetched fresh prices, decremented stock"

      stock_after=$(curl -s http://localhost:8083/api/products/$prod_id | grep -o '"stockQuantity":[0-9]*' | grep -o '[0-9]*')
      if [ "$stock_after" -lt "$stock_before" ]; then
        echo "OK: product-service stock actually decremented ($stock_before -> $stock_after)"
      else
        echo "FAIL: stock did not decrement ($stock_before -> $stock_after) -- check order-service's ProductClient.adjustStock call"
      fi

      cart_json=$(curl -s http://localhost:8084/api/cart -H "Authorization: Bearer $token")
      if echo "$cart_json" | grep -q '"totalItems":0'; then
        echo "OK: cart-service's cart was cleared by order-service after placement"
      else
        echo "FAIL: cart was not cleared -- $cart_json"
      fi
    else
      echo "SKIP: order-service not running -- add it to test the full checkout chain"
    fi
  else
    echo "SKIP: cart-service not running -- add it to test the cross-service fetch path"
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
