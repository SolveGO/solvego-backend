import argparse
import json
import shlex
import subprocess
import sys
import time
from pathlib import Path
from typing import Any


PROJECT_DIR = Path(__file__).resolve().parents[1]
K6_SCRIPT = PROJECT_DIR / "k6/tests/problem-cache-performance.js"
RESULT_ROOT = PROJECT_DIR / "k6/results/cache-comparison"

DEFAULT_BASE_URL = "http://13.209.14.133:8080"
DEFAULT_SSH_HOST = "13.209.14.133"
DEFAULT_SSH_USER = "ubuntu"
DEFAULT_SSH_KEY = Path("~/.ssh/solvego-ec2-key.pem").expanduser()

# EC2에서 solvego 프로젝트가 위치한 경로
DEFAULT_REMOTE_DIR = "/home/ubuntu/solvego"

REDIS_CONTAINER = "solvego-redis"
CACHE_NAME = "problemPages"


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "EC2의 Spring Cache 설정을 자동으로 변경하고, "
            "Mac에서 k6 부하 테스트를 실행합니다."
        )
    )

    parser.add_argument(
        "--mode",
        required=True,
        choices=["cache-off", "cache-on"],
        help="실험할 캐시 상태",
    )
    parser.add_argument(
        "--base-url",
        default=DEFAULT_BASE_URL,
        help="부하 테스트 대상 서버 주소",
    )
    parser.add_argument(
        "--vus",
        type=int,
        default=20,
        help="동시 가상 사용자 수",
    )
    parser.add_argument(
        "--duration",
        default="30s",
        help="측정 지속 시간",
    )
    parser.add_argument(
        "--page",
        type=int,
        default=0,
        help="조회 페이지 번호",
    )
    parser.add_argument(
        "--size",
        type=int,
        default=20,
        help="페이지 크기",
    )
    parser.add_argument(
        "--run",
        type=int,
        help=(
            "실험 회차. 생략하면 기존 결과 파일을 확인해 "
            "다음 회차를 자동으로 선택합니다."
        ),
    )
    parser.add_argument(
        "--ssh-host",
        default=DEFAULT_SSH_HOST,
        help="EC2 호스트 주소",
    )
    parser.add_argument(
        "--ssh-user",
        default=DEFAULT_SSH_USER,
        help="EC2 SSH 사용자",
    )
    parser.add_argument(
        "--ssh-key",
        type=Path,
        default=DEFAULT_SSH_KEY,
        help="EC2 SSH Private Key 경로",
    )
    parser.add_argument(
        "--remote-dir",
        default=DEFAULT_REMOTE_DIR,
        help="EC2 내부 SolveGO 프로젝트 경로",
    )
    parser.add_argument(
        "--health-timeout",
        type=int,
        default=120,
        help="애플리케이션 Health Check 최대 대기 시간(초)",
    )

    return parser.parse_args()


def run_command(
        command: list[str],
        *,
        capture_output: bool = False,
        allowed_return_codes: set[int] | None = None,
) -> subprocess.CompletedProcess[str]:
    print(f"\n$ {shlex.join(command)}")

    result = subprocess.run(
        command,
        cwd=PROJECT_DIR,
        check=False,
        text=True,
        capture_output=capture_output,
    )

    allowed_codes = (
        allowed_return_codes
        if allowed_return_codes is not None
        else {0}
    )

    if result.returncode not in allowed_codes:
        raise subprocess.CalledProcessError(
            result.returncode,
            command,
            output=result.stdout,
            stderr=result.stderr,
        )

    return result


def build_ssh_command(
        *,
        ssh_host: str,
        ssh_user: str,
        ssh_key: Path,
        remote_command: str,
) -> list[str]:
    return [
        "ssh",
        "-i",
        str(ssh_key),
        "-o",
        "StrictHostKeyChecking=accept-new",
        f"{ssh_user}@{ssh_host}",
        remote_command,
    ]


def run_remote_command(
        *,
        ssh_host: str,
        ssh_user: str,
        ssh_key: Path,
        remote_command: str,
        capture_output: bool = False,
) -> subprocess.CompletedProcess[str]:
    command = build_ssh_command(
        ssh_host=ssh_host,
        ssh_user=ssh_user,
        ssh_key=ssh_key,
        remote_command=remote_command,
    )

    return run_command(
        command,
        capture_output=capture_output,
    )


def validate_environment(
        *,
        ssh_key: Path,
) -> None:
    if not K6_SCRIPT.exists():
        raise FileNotFoundError(
            f"k6 테스트 파일을 찾지 못했습니다: {K6_SCRIPT}"
        )

    if not ssh_key.exists():
        raise FileNotFoundError(
            f"SSH Key를 찾지 못했습니다: {ssh_key}"
        )

    if not ssh_key.is_file():
        raise FileNotFoundError(
            f"SSH Key가 파일이 아닙니다: {ssh_key}"
        )

    print("k6 설치 확인")
    run_command(["k6", "version"])

    print("\nSSH 연결 확인")
    run_command(["ssh", "-V"])


def get_cache_type(mode: str) -> str:
    if mode == "cache-off":
        return "none"

    return "redis"


def recreate_remote_app(
        *,
        mode: str,
        ssh_host: str,
        ssh_user: str,
        ssh_key: Path,
        remote_dir: str,
) -> None:
    cache_type = get_cache_type(mode)

    safe_remote_dir = shlex.quote(remote_dir)
    safe_cache_type = shlex.quote(cache_type)

    remote_command = (
        "set -e; "
        f"cd {safe_remote_dir}; "
        f"CACHE_TYPE={safe_cache_type} "
        "docker compose up -d --force-recreate app; "
        "docker compose ps app"
    )

    print("\n" + "=" * 60)
    print("EC2 애플리케이션 재시작")
    print("=" * 60)
    print(f"Mode       : {mode}")
    print(f"CACHE_TYPE : {cache_type}")
    print("=" * 60)

    run_remote_command(
        ssh_host=ssh_host,
        ssh_user=ssh_user,
        ssh_key=ssh_key,
        remote_command=remote_command,
    )


def wait_for_health(
        *,
        base_url: str,
        timeout_seconds: int,
) -> None:
    health_url = f"{base_url.rstrip('/')}/actuator/health"
    deadline = time.monotonic() + timeout_seconds

    print("\n원격 서버가 준비될 때까지 대기합니다.")

    while time.monotonic() < deadline:
        result = subprocess.run(
            [
                "curl",
                "--silent",
                "--show-error",
                "--fail",
                health_url,
            ],
            cwd=PROJECT_DIR,
            text=True,
            capture_output=True,
        )

        if result.returncode == 0:
            print(f"Health Check 성공: {result.stdout.strip()}")
            return

        print("애플리케이션 시작 대기 중...")
        time.sleep(2)

    raise RuntimeError(
        f"{timeout_seconds}초 안에 서버가 준비되지 않았습니다: "
        f"{health_url}"
    )


def get_cache_key(
        *,
        page: int,
        size: int,
) -> str:
    return f"{CACHE_NAME}::page:{page}:size:{size}"


def clear_cache_key(
        *,
        page: int,
        size: int,
        ssh_host: str,
        ssh_user: str,
        ssh_key: Path,
) -> None:
    cache_key = get_cache_key(page=page, size=size)

    remote_command = (
        f"docker exec {shlex.quote(REDIS_CONTAINER)} "
        f"redis-cli DEL {shlex.quote(cache_key)}"
    )

    print(f"\nRedis 캐시 키 삭제: {cache_key}")

    run_remote_command(
        ssh_host=ssh_host,
        ssh_user=ssh_user,
        ssh_key=ssh_key,
        remote_command=remote_command,
    )


def warm_up_cache(
        *,
        base_url: str,
        page: int,
        size: int,
) -> None:
    endpoint = (
        f"{base_url.rstrip('/')}"
        f"/api/problems?page={page}&size={size}"
    )

    print("\nRedis 캐시 워밍업")
    run_command(
        [
            "curl",
            "--silent",
            "--show-error",
            "--fail",
            "--output",
            "/dev/null",
            endpoint,
        ]
    )


def verify_cache_key(
        *,
        page: int,
        size: int,
        ssh_host: str,
        ssh_user: str,
        ssh_key: Path,
) -> None:
    cache_key = get_cache_key(page=page, size=size)

    remote_command = (
        f"docker exec {shlex.quote(REDIS_CONTAINER)} "
        f"redis-cli EXISTS {shlex.quote(cache_key)}"
    )

    result = run_remote_command(
        ssh_host=ssh_host,
        ssh_user=ssh_user,
        ssh_key=ssh_key,
        remote_command=remote_command,
        capture_output=True,
    )

    output = result.stdout.strip()

    if output != "1":
        raise RuntimeError(
            f"Redis 캐시 키가 생성되지 않았습니다: {cache_key}"
        )

    print(f"Redis 캐시 키 확인 완료: {cache_key}")


def find_next_run_number(
        *,
        mode: str,
        vus: int,
) -> int:
    RESULT_ROOT.mkdir(parents=True, exist_ok=True)

    pattern = f"{mode}-vus{vus}-run*.json"
    existing_paths = RESULT_ROOT.glob(pattern)

    run_numbers: list[int] = []

    prefix = f"{mode}-vus{vus}-run"

    for path in existing_paths:
        run_text = path.stem.removeprefix(prefix)

        try:
            run_numbers.append(int(run_text))
        except ValueError:
            continue

    if not run_numbers:
        return 1

    return max(run_numbers) + 1


def create_result_path(
        *,
        mode: str,
        vus: int,
        run_number: int | None,
) -> tuple[Path, int]:
    RESULT_ROOT.mkdir(parents=True, exist_ok=True)

    selected_run = (
        run_number
        if run_number is not None
        else find_next_run_number(mode=mode, vus=vus)
    )

    if selected_run < 1:
        raise ValueError("--run은 1 이상의 정수여야 합니다.")

    result_path = (
            RESULT_ROOT
            / f"{mode}-vus{vus}-run{selected_run}.json"
    )

    if result_path.exists():
        raise FileExistsError(
            f"이미 같은 실험 결과가 존재합니다: {result_path}"
        )

    return result_path, selected_run

def run_k6(
        *,
        mode: str,
        base_url: str,
        vus: int,
        duration: str,
        page: int,
        size: int,
        run_number: int,
        result_path: Path,
) -> None:
    print("\n" + "=" * 60)
    print("SolveGO Redis Cache Performance Test")
    print("=" * 60)
    print(f"Mode      : {mode}")
    print(f"Run       : {run_number}")
    print(f"Target    : {base_url}")
    print(f"VUs       : {vus}")
    print(f"Duration  : {duration}")
    print(f"Endpoint  : /api/problems?page={page}&size={size}")
    print(f"Result    : {result_path}")
    print("=" * 60)

    command = [
        "k6",
        "run",
        "--summary-export",
        str(result_path),
        "-e",
        f"BASE_URL={base_url}",
        "-e",
        f"VUS={vus}",
        "-e",
        f"DURATION={duration}",
        "-e",
        f"PAGE={page}",
        "-e",
        f"SIZE={size}",
        "-e",
        f"EXPERIMENT_MODE={mode}",
        "-e",
        f"RUN_NUMBER={run_number}",
        str(K6_SCRIPT),
    ]

    result = run_command(
        command,
        allowed_return_codes={0, 99},
    )

    if result.returncode == 99:
        print()
        print("⚠️ k6 threshold를 만족하지 못했습니다.")
        print("JSON 결과는 정상 저장되었으며 다음 실험을 계속합니다.")

def get_metric(
        summary: dict[str, Any],
        metric_name: str,
        value_name: str,
) -> float | None:
    metric = summary.get("metrics", {}).get(metric_name)

    if not isinstance(metric, dict):
        return None

    nested_values = metric.get("values")

    if isinstance(nested_values, dict):
        value = nested_values.get(value_name)

        if isinstance(value, (int, float)):
            return float(value)

    value = metric.get(value_name)

    if isinstance(value, (int, float)):
        return float(value)

    return None


def print_summary(result_path: Path) -> None:
    with result_path.open(encoding="utf-8") as file:
        summary = json.load(file)

    metrics = [
        ("평균 응답시간", "http_req_duration", "avg", "ms"),
        ("중앙값", "http_req_duration", "med", "ms"),
        ("p90", "http_req_duration", "p(90)", "ms"),
        ("p95", "http_req_duration", "p(95)", "ms"),
        ("최솟값", "http_req_duration", "min", "ms"),
        ("최댓값", "http_req_duration", "max", "ms"),
        ("총 요청 수", "http_reqs", "count", "requests"),
        ("처리량", "http_reqs", "rate", "req/s"),
        ("실패율", "http_req_failed", "rate", ""),
    ]

    print("\n===== 실험 결과 =====")

    for label, metric_name, value_name, unit in metrics:
        value = get_metric(
            summary,
            metric_name,
            value_name,
        )

        if value is None:
            continue

        if (
                value_name == "rate"
                and metric_name == "http_req_failed"
        ):
            print(f"{label:<14}: {value * 100:.4f}%")
        elif unit == "requests":
            print(f"{label:<14}: {value:.0f} {unit}")
        else:
            print(f"{label:<14}: {value:.4f} {unit}")

    print(f"\nJSON 결과: {result_path}")


def prepare_remote_server(
        *,
        mode: str,
        base_url: str,
        page: int,
        size: int,
        ssh_host: str,
        ssh_user: str,
        ssh_key: Path,
        remote_dir: str,
        health_timeout: int,
) -> None:
    recreate_remote_app(
        mode=mode,
        ssh_host=ssh_host,
        ssh_user=ssh_user,
        ssh_key=ssh_key,
        remote_dir=remote_dir,
    )

    wait_for_health(
        base_url=base_url,
        timeout_seconds=health_timeout,
    )

    if mode == "cache-on":
        clear_cache_key(
            page=page,
            size=size,
            ssh_host=ssh_host,
            ssh_user=ssh_user,
            ssh_key=ssh_key,
        )

        warm_up_cache(
            base_url=base_url,
            page=page,
            size=size,
        )

        verify_cache_key(
            page=page,
            size=size,
            ssh_host=ssh_host,
            ssh_user=ssh_user,
            ssh_key=ssh_key,
        )


def main() -> None:
    args = parse_arguments()

    ssh_key = args.ssh_key.expanduser().resolve()
    base_url = args.base_url.rstrip("/")

    try:
        validate_environment(ssh_key=ssh_key)

        result_path, run_number = create_result_path(
            mode=args.mode,
            vus=args.vus,
            run_number=args.run,
        )

        prepare_remote_server(
            mode=args.mode,
            base_url=base_url,
            page=args.page,
            size=args.size,
            ssh_host=args.ssh_host,
            ssh_user=args.ssh_user,
            ssh_key=ssh_key,
            remote_dir=args.remote_dir,
            health_timeout=args.health_timeout,
        )

        run_k6(
            mode=args.mode,
            base_url=base_url,
            vus=args.vus,
            duration=args.duration,
            page=args.page,
            size=args.size,
            run_number=run_number,
            result_path=result_path,
        )

        print_summary(result_path)

    except subprocess.CalledProcessError as error:
        print(
            f"\n명령 실행에 실패했습니다. "
            f"exit code={error.returncode}",
            file=sys.stderr,
        )
        sys.exit(error.returncode)

    except (
            FileNotFoundError,
            FileExistsError,
            RuntimeError,
            ValueError,
            json.JSONDecodeError,
    ) as error:
        print(
            f"\n실험 실행 실패: {error}",
            file=sys.stderr,
        )
        sys.exit(1)


if __name__ == "__main__":
    main()