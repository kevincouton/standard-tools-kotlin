#!/usr/bin/env bash
# Reads Gradle test output from stdin and prints a compact scenario tree summary.
# Intended for: ./gradlew e2eTest --info | ./scripts/visual-test-report.sh
set -euo pipefail

SCENARIO_PATTERN='E2E Scenario:'
STEP_PATTERN='[REST]|\[gRPC\]|\[A2A\]|\[MCP\]|\[HTTP\]|\[JSON-RPC\]'

print_summary() {
  local in_scenario=false
  local scenario_name=""

  while IFS= read -r line; do
    if [[ "$line" == *"$SCENARIO_PATTERN"* ]]; then
      scenario_name="${line##*E2E Scenario: }"
      echo ""
      echo "🧪 $scenario_name"
      in_scenario=true
      continue
    fi

    if [[ "$in_scenario" == true && "$line" =~ ^[[:space:]]*(├─|└─)[[:space:]]+(\[.*\].*)$ ]]; then
      echo "  ${BASH_REMATCH[1]} ${BASH_REMATCH[2]}"
      if [[ "${BASH_REMATCH[1]}" == "└─" ]]; then
        in_scenario=false
      fi
    fi
  done
}

print_summary

echo ""
echo "📊 Visual test summary complete."
