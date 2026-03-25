#!/bin/zsh

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
PID_DIR="$ROOT_DIR/.run/pids"

if [ ! -d "$PID_DIR" ]; then
  echo "No PID directory found. Nothing to stop."
  exit 0
fi

found_any=false

for pid_file in "$PID_DIR"/*.pid; do
  if [ ! -f "$pid_file" ]; then
    continue
  fi

  found_any=true
  service="$(basename "$pid_file" .pid)"
  pid="$(cat "$pid_file")"

  if kill -0 "$pid" 2>/dev/null; then
    echo "Stopping $service (PID $pid) ..."
    kill "$pid"
  else
    echo "$service is not running, removing stale PID file."
  fi

  rm -f "$pid_file"
done

if [ "$found_any" = false ]; then
  echo "No PID files found. Nothing to stop."
fi
