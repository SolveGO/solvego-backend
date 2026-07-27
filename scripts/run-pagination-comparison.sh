#!/bin/bash

set -euo pipefail

RESULT_DIR="k6/results"
SCRIPT_PATH="k6/tests/problem-list-comparison.js"
mkdir -p "$RESULT_DIR"

run_test() {
    local endpoint=$1
    local vus=$2
    local run=$3

    local output_file="${RESULT_DIR}/${endpoint}-vus-${vus}-run-${run}.json"

    echo
    echo "========================================"
    echo "Running: endpoint=${endpoint}, VUS=${vus}, run=${run}"
    echo "========================================"

    k6 run \
        -e ENV=ec2 \
        -e ENDPOINT="$endpoint" \
        -e VUS="$vus" \
        --summary-export="$output_file" \
        "$SCRIPT_PATH"
}

VUS_VALUES=(1 5 10 20 30)
REPEAT_COUNT=3
COOLDOWN_SECONDS=10

for vus in "${VUS_VALUES[@]}"; do
    for run in $(seq 1 "$REPEAT_COUNT"); do
        run_test "legacy" "$vus" "$run"

        sleep "$COOLDOWN_SECONDS"

        run_test "paginated" "$vus" "$run"

        sleep "$COOLDOWN_SECONDS"
    done
done

python3 k6/generate-report.py

echo
echo "Benchmark completed."
echo "Report: ${RESULT_DIR}/pagination-comparison.md"