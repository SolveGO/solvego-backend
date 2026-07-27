#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PYTHON_SCRIPT="${SCRIPT_DIR}/run-cache-experiment.py"

VUS_LIST=(20 50 100 200 500 1000)
REPEAT_COUNT=3
DURATION="30s"

for vus in "${VUS_LIST[@]}"; do
    echo
    echo "========================================"
    echo "VU ${vus} 실험 시작"
    echo "========================================"

    for ((run = 1; run <= REPEAT_COUNT; run++)); do
        echo
        echo "[${vus} VU] ${run}회차 Cache OFF"

        python3 "${PYTHON_SCRIPT}" \
            --mode cache-off \
            --vus "${vus}" \
            --duration "${DURATION}"

        sleep 30

        echo
        echo "[${vus} VU] ${run}회차 Cache ON"

        python3 "${PYTHON_SCRIPT}" \
            --mode cache-on \
            --vus "${vus}" \
            --duration "${DURATION}"

        sleep 30
    done

    echo
    echo "VU ${vus} 실험 완료"
    sleep 60
done

echo
echo "모든 실험 완료"