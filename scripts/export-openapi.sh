#!/usr/bin/env bash
# ==============================================================================
# Export OpenAPI Specification (Bash)
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"
DOCS_API_DIR="$ROOT_DIR/docs/api"
mkdir -p "$DOCS_API_DIR"

OUTPUT_FILE="$DOCS_API_DIR/openapi.json"
BACKEND_DIR="$ROOT_DIR/backend"
JAR_PATH="$BACKEND_DIR/target/fitflow-backend-0.0.1-SNAPSHOT.jar"

if [ ! -f "$JAR_PATH" ]; then
    echo "Jar not found. Building backend..."
    (cd "$BACKEND_DIR" && mvn package -DskipTests)
fi

APP_STARTED_HERE=0
APP_PID=""

# Check if port 8080 is reachable
if ! curl -s --max-time 2 http://localhost:8080/v3/api-docs > /dev/null 2>&1; then
    echo "Starting FitFlow backend in dev profile..."
    java -jar "$JAR_PATH" --spring.profiles.active=dev > "$BACKEND_DIR/target/openapi-export.log" 2>&1 &
    APP_PID=$!
    APP_STARTED_HERE=1

    READY=0
    for i in {1..45}; do
        sleep 2
        if curl -s --max-time 2 http://localhost:8080/v3/api-docs > /dev/null 2>&1; then
            READY=1
            break
        fi
    done

    if [ "$READY" -ne 1 ]; then
        echo "Backend failed to start or /v3/api-docs did not become available."
        if [ -n "$APP_PID" ]; then
            kill -9 "$APP_PID" 2>/dev/null || true
        fi
        exit 1
    fi
fi

cleanup() {
    if [ "$APP_STARTED_HERE" -eq 1 ] && [ -n "$APP_PID" ]; then
        echo "Stopping temporary backend process..."
        kill -9 "$APP_PID" 2>/dev/null || true
    fi
}
trap cleanup EXIT

echo "Fetching OpenAPI documentation from http://localhost:8080/v3/api-docs..."
node -e '
const fs = require("fs");
const http = require("http");

function sortObject(obj) {
  if (obj === null || typeof obj !== "object") return obj;
  if (Array.isArray(obj)) return obj.map(sortObject);
  const sorted = {};
  Object.keys(obj).sort().forEach(k => {
    sorted[k] = sortObject(obj[k]);
  });
  return sorted;
}

http.get("http://localhost:8080/v3/api-docs", res => {
  let data = "";
  res.on("data", chunk => data += chunk);
  res.on("end", () => {
    const raw = JSON.parse(data);
    const sorted = sortObject(raw);
    fs.writeFileSync(process.argv[1], JSON.stringify(sorted, null, 2) + "\n", "utf8");
    console.log("OpenAPI specification exported to " + process.argv[1]);
  });
}).on("error", err => {
  console.error("HTTP fetch error: " + err.message);
  process.exit(1);
});
' "$OUTPUT_FILE"
