import random
import subprocess
import statistics
import time
import pymysql

CONTAINER = "solvego-db"
DATABASE = "solvego"
WARMUP_COUNT = 10
MEASURE_COUNT = 100


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


def docker_inspect(format_string: str) -> str:
    result = subprocess.run(
        ["docker", "inspect", "--format", format_string, CONTAINER],
        check=True,
        capture_output=True,
        text=True,
    )
    return result.stdout.strip()

def get_mysql_password() -> str:
    environment = docker_inspect(
        "{{range .Config.Env}}{{println .}}{{end}}"
    )

    for line in environment.splitlines():
        if line.startswith("MYSQL_ROOT_PASSWORD="):
            return line.split("=", 1)[1]

    raise RuntimeError("MYSQL_ROOT_PASSWORD를 찾지 못했습니다.")


def execute_query(cursor, query: str) -> None:
    cursor.execute(query)
    cursor.fetchall()


def measure_query(cursor, query: str) -> float:
    start = time.perf_counter_ns()

    cursor.execute(query)
    cursor.fetchall()

    end = time.perf_counter_ns()

    return (end - start) / 1_000_000

def print_statistics(name: str, values: list[float]) -> None:
    print(f"\n[{name}]")
    print(f"실행 횟수: {len(values)}회")
    print(f"평균: {statistics.mean(values):.4f} ms")
    print(f"중앙값: {statistics.median(values):.4f} ms")
    print(f"최솟값: {min(values):.4f} ms")
    print(f"최댓값: {max(values):.4f} ms")
    print(f"표준편차: {statistics.stdev(values):.4f} ms")


def main() -> None:

    password = get_mysql_password()
    container_ip = docker_inspect(
        "{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}"
    )

    if not container_ip:
        raise RuntimeError("MySQL 컨테이너 IP를 찾지 못했습니다.")

    connection = pymysql.connect(
        host=container_ip,
        port=3306,
        user="root",
        password=password,
        database=DATABASE,
        charset="utf8mb4",
        autocommit=True,
    )

    results = {}
    for name in QUERIES:
        results[name] = []

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
                # Randomize the query order to reduce execution-order bias.
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
        print_statistics(name, values)

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




if __name__ == "__main__":
    main()