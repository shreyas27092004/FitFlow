#!/usr/bin/env bash
set -e

echo "=== FitFlow Live Agent Evaluation Runner ==="

if [ -n "$1" ]; then
    export GEMINI_API_KEY="$1"
fi

if [ -z "$GEMINI_API_KEY" ]; then
    echo "WARNING: GEMINI_API_KEY is not set. LiveAgentEvalIT will be skipped by assumption."
fi

export FITFLOW_LIVE_EVAL="1"

cleanup() {
    export FITFLOW_LIVE_EVAL="0"
}
trap cleanup EXIT

echo "Running LiveAgentEvalIT against live Gemini model..."
mvn -f backend/pom.xml test -Dtest=LiveAgentEvalIT

echo "=== Live Evaluation Run Completed ==="
