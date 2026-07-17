import statistics
import subprocess
import time
import pymysql


CONTAINER = "solvego-db"
DATABASE = "solvego"
WARMUP_RUNS = 10
MEASURED_RUNS = 100

NO_INDEX_QUERY = """
SELECT *
FROM problems IGNORE INDEX (idx_problems_created_at)
ORDER BY created_at DESC
LIMIT 20
"""

INDEX_QUERY = """
SELECT *
FROM problems
ORDER BY created_at DESC
LIMIT 20
"""


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

    no_index_times = []
    index_times = []

    try:
        with connection.cursor() as cursor:
            print("워밍업을 시작합니다.")

            for _ in range(WARMUP_RUNS):
                cursor.execute(NO_INDEX_QUERY)
                cursor.fetchall()

                cursor.execute(INDEX_QUERY)
                cursor.fetchall()

            print("워밍업 결과는 폐기했습니다.")
            print("100회 측정을 시작합니다.")

            # 실행 순서에 따른 편향을 줄이기 위해 순서를 번갈아 실행
            for run in range(MEASURED_RUNS):
                if run % 2 == 0:
                    no_index_times.append(
                        measure_query(cursor, NO_INDEX_QUERY)
                    )
                    index_times.append(
                        measure_query(cursor, INDEX_QUERY)
                    )
                else:
                    index_times.append(
                        measure_query(cursor, INDEX_QUERY)
                    )
                    no_index_times.append(
                        measure_query(cursor, NO_INDEX_QUERY)
                    )

                if (run + 1) % 10 == 0:
                    print(f"{run + 1}/{MEASURED_RUNS}회 완료")

    finally:
        connection.close()

    print_statistics(
        "인덱스 미사용: Table Scan + Sort",
        no_index_times,
    )
    print_statistics(
        "인덱스 사용: Index Scan",
        index_times,
    )

    average_ratio = (
            statistics.mean(no_index_times)
            / statistics.mean(index_times)
    )
    median_ratio = (
            statistics.median(no_index_times)
            / statistics.median(index_times)
    )

    print("\n[성능 차이]")
    print(f"평균 기준: 약 {average_ratio:.2f}배")
    print(f"중앙값 기준: 약 {median_ratio:.2f}배")


if __name__ == "__main__":
    main()