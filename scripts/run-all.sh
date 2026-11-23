#!/bin/bash
# Запускает все сервисы (каждый - отдельный процесс). Предварительно: docker compose up -d (в infra/) и mvn -q -DskipTests package.
cd "$(dirname "$0")/.."
mkdir -p logs
if [ -z "$JAVA_HOME" ] && [ -x /usr/libexec/java_home ]; then export JAVA_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null); fi
JAVA=${JAVA_HOME:+$JAVA_HOME/bin/}java
SERVICES="auth-service borrower-service statement-service analysis-service scoring-service notification-service application-service graphql-gateway"
only="$@"
for s in ${only:-$SERVICES}; do
  jar=$(ls $s/target/$s-*.jar 2>/dev/null | grep -v original | head -1)
  if [ -z "$jar" ]; then echo "Нет jar для $s: выполните mvn -q -DskipTests package"; continue; fi
  if [ -f logs/$s.pid ] && kill -0 $(cat logs/$s.pid) 2>/dev/null; then echo "$s уже запущен"; continue; fi
  # отдельная сессия ОС: процесс не завершается вместе с терминалом
  python3 -c "import subprocess,sys; p=subprocess.Popen(['$JAVA','-jar','$jar'],stdout=open('logs/$s.log','w'),stderr=subprocess.STDOUT,stdin=subprocess.DEVNULL,start_new_session=True); open('logs/$s.pid','w').write(str(p.pid))"
  echo "запущен $s (pid $(cat logs/$s.pid))"
done
