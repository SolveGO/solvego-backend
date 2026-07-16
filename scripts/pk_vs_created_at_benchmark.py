import csv
import os
import random
import statistics
import sys
import time
from typing import Dict, List

import pymysql


WARMUP_COUNT = 10
MEASURE_COUNT = 100
OUTPUT_FILE = "pk_vs_created_at_benchmark_results.csv"

QUERIES = {
    "created_at_without_index": """
        SELECT *
        FROM problems IGNORE INDEX (idx_problems_created_at)
        ORDER BY created_at DESC
        LIMIT 20
    """,
    "created_at_with_index": """
        SELECT *
        FROM problems
        ORDER BY created_at DESC
        LIMIT 20
    """,
    "primary_key_desc": """
        SELECT *
        FROM problems
        ORDER BY id DESC
        LIMIT 20
    """,
}


def execute_query(cursor, query: str) -> None:
    cursor.execute(query)
    cursor.fetchall()


def measure_query(cursor, query: str) -> float:
    start_ns = time.perf_counter_ns()

    cursor.execute(query)
    cursor.fetchall()

    end_ns = time.perf_counter_ns()

    return (end_ns - start_ns) / 1_000_000


def print_summary(name: str, values: List[float]) -> None:
    print(f"\n[{name}]")
    print(f"count   : {len(values)}")
    print(f"mean    : {statistics.mean(values):.4f} ms")
    print(f"median  : {statistics.median(values):.4f} ms")
    print(f"min     : {min(values):.4f} ms")
    print(f"max     : {max(values):.4f} ms")
    print(f"stdev   : {statistics.stdev(values):.4f} ms")


def main() -> None:
    db_host = os.getenv("DB_HOST", "127.0.0.1")
    db_port = int(os.getenv("DB_PORT", "3307"))
    db_name = os.getenv("DB_NAME", "solvego")
    db_user = os.getenv("DB_USER")
    db_password = os.getenv("DB_PASSWORD")

    if not db_user or not db_password:
        print("DB_USER와 DB_PASSWORD 환경변수가 필요합니다.")
        sys.exit(1)

    connection = pymysql.connect(
        host=db_host,
        port=db_port,
        user=db_user,
        password=db_password,
        database=db_name,
        charset="utf8mb4",
        autocommit=True,
        cursorclass=pymysql.cursors.Cursor,
        connect_timeout=10,
        read_timeout=30,
        write_timeout=30,
    )

    results: Dict[str, List[float]] = {
        name: [] for name in QUERIES
    }

    try:
        with connection.cursor() as cursor:
            cursor.execute("SELECT COUNT(*) FROM problems")
            row_count = cursor.fetchone()[0]

            print(f"problems row count: {row_count}")
            print(f"warm-up: {WARMUP_COUNT}회")
            print(f"measurement: 각 {MEASURE_COUNT}회")

            print("\n워밍업 시작")

            for name, query in QUERIES.items():
                for _ in range(WARMUP_COUNT):
                    execute_query(cursor, query)

                print(f"- {name}: 워밍업 완료")

            print("\n측정 시작")

            query_names = list(QUERIES.keys())

            for iteration in range(1, MEASURE_COUNT + 1):
                random.shuffle(query_names)

                for name in query_names:
                    elapsed_ms = measure_query(
                        cursor,
                        QUERIES[name],
                    )
                    results[name].append(elapsed_ms)

                if iteration % 10 == 0:
                    print(f"{iteration}/{MEASURE_COUNT} 완료")

    finally:
        connection.close()

    print("\n===== 결과 요약 =====")

    for name, values in results.items():
        print_summary(name, values)

    index_mean = statistics.mean(
        results["created_at_with_index"]
    )
    no_index_mean = statistics.mean(
        results["created_at_without_index"]
    )
    pk_mean = statistics.mean(
        results["primary_key_desc"]
    )

    print("\n===== 평균 실행 시간 비교 =====")
    print(
        "created_at 인덱스 적용은 미적용보다 "
        f"{no_index_mean / index_mean:.2f}배 빠름"
    )
    print(
        "PK 역순은 created_at 인덱스 방식보다 "
        f"{index_mean / pk_mean:.2f}배 빠름"
        if pk_mean < index_mean
        else
        "created_at 인덱스 방식은 PK 역순보다 "
        f"{pk_mean / index_mean:.2f}배 빠름"
    )

    with open(
            OUTPUT_FILE,
            "w",
            newline="",
            encoding="utf-8",
    ) as csv_file:
        writer = csv.writer(csv_file)
        writer.writerow(
            [
                "query_type",
                "iteration",
                "elapsed_ms",
            ]
        )

        for name, values in results.items():
            for iteration, elapsed_ms in enumerate(
                    values,
                    start=1,
            ):
                writer.writerow(
                    [
                        name,
                        iteration,
                        f"{elapsed_ms:.6f}",
                    ]
                )

    print(f"\nCSV 저장 완료: {OUTPUT_FILE}")


if __name__ == "__main__":
    main()