#!/bin/bash
# Останавливает сервисы (или только перечисленные).
cd "$(dirname "$0")/.."
SERVICES="graphql-gateway application-service notification-service scoring-service analysis-service statement-service borrower-service auth-service"
only="$@"
for s in ${only:-$SERVICES}; do
  if [ -f logs/$s.pid ]; then
    pid=$(cat logs/$s.pid)
    if kill -0 $pid 2>/dev/null; then kill $pid; echo "остановлен $s"; fi
    rm -f logs/$s.pid
  fi
done
