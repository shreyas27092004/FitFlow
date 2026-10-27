#!/usr/bin/env bash
# ==============================================================================
# FitFlow Verification Script (Bash)
# ==============================================================================
set -e

# Load .env if present
if [ -f .env ]; then
  export $(grep -v '^#' .env | xargs)
fi

echo -e "\n=== FitFlow Verification Suite ==="

# 1. Environment checks
echo "1. Checking environment configurations..."
MYSQL_URL=${MYSQL_URL:-"jdbc:mysql://localhost:3306/fitflow"}
PG_URL=${PG_URL:-"jdbc:postgresql://localhost:5432/fitflow"}

# 2. Port checks
echo "2. Checking TCP port connectivity..."
nc -z 127.0.0.1 3306 && echo "MySQL Port 3306: PASS" || echo "MySQL Port 3306: FAIL"
nc -z 127.0.0.1 5432 && echo "Postgres Port 5432: PASS" || echo "Postgres Port 5432: FAIL"
nc -z 127.0.0.1 5672 && echo "RabbitMQ Port 5672: PASS" || echo "RabbitMQ Port 5672: OPTIONAL"

# 3. Maven verify
echo "3. Running Maven verify..."
cd backend
mvn verify
cd ..

# 4. Boot Dev and health check
echo "4. Booting dev app for health verification..."
cd backend
java -jar target/fitflow-backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev &
APP_PID=$!
cd ..

HEALTH_UP=false
for i in {1..30}; do
  sleep 3
  STATUS=$(curl -s http://localhost:8080/actuator/health | grep '"status":"UP"' || true)
  if [ -n "$STATUS" ]; then
    HEALTH_UP=true
    break
  fi
done

kill -9 $APP_PID || true

if [ "$HEALTH_UP" = true ]; then
  echo "Live Health (/actuator/health UP): PASS"
  echo -e "\nAll verification gates PASSED!"
  exit 0
else
  echo "Live Health (/actuator/health UP): FAIL"
  echo -e "\nVerification FAILED!"
  exit 1
fi
