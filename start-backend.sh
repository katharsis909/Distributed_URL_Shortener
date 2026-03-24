#!/bin/zsh

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
BACKEND_DIR="$ROOT_DIR/backend"
RUN_DIR="$ROOT_DIR/.run"
LOG_DIR="$RUN_DIR/logs"
PID_DIR="$RUN_DIR/pids"

mkdir -p "$LOG_DIR" "$PID_DIR"

if command -v mvn >/dev/null 2>&1; then
  MVN_CMD="mvn"
elif [ -x "$HOME/.local/bin/mvn" ]; then
  MVN_CMD="$HOME/.local/bin/mvn"
else
  echo "Maven not found. Install mvn or ensure $HOME/.local/bin/mvn exists."
  exit 1
fi

SERVICES=(
  "URL_Service1"
  "URL_Service2"
  "URL_Service3"
  "URL_Service4"
  "RouterService"
)

for service in "${SERVICES[@]}"; do
  pid_file="$PID_DIR/${service}.pid"
  if [ -f "$pid_file" ] && kill -0 "$(cat "$pid_file")" 2>/dev/null; then
    echo "$service is already running with PID $(cat "$pid_file")"
    continue
  fi

  service_dir="$BACKEND_DIR/$service"
  log_file="$LOG_DIR/${service}.log"

  echo "Starting $service ..."
  (
    cd "$service_dir"
    nohup "$MVN_CMD" spring-boot:run >"$log_file" 2>&1 &
    echo $! >"$pid_file"
  )
done

echo ""
echo "Backend start requested."
echo "Logs: $LOG_DIR"
echo "PIDs: $PID_DIR"
echo "Use ./stop-backend.sh to stop all backend services."
