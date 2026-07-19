import json
import statistics
from pathlib import Path
from typing import Any

RESULT_DIR = Path("k6/results")
OUTPUT_FILE = RESULT_DIR / "pagination-comparison.md"

VUS_VALUES = [1, 5, 10, 20, 30]
ENDPOINTS = ["legacy", "paginated"]
REPEAT_COUNT = 3


def load_result(endpoint: str, vus: int, run: int) -> dict[str, Any]:
    path = RESULT_DIR / f"{endpoint}-vus-{vus}-run-{run}.json"

    if not path.exists():
        raise FileNotFoundError(f"Result file not found: {path}")

    with path.open(encoding="utf-8") as file:
        return json.load(file)


def metric_value(
        result: dict[str, Any],
        metric_name: str,
        value_name: str,
        default: float = 0.0,
) -> float:
    metric = result.get("metrics", {}).get(metric_name, {})

    # handleSummary() 형식
    if "values" in metric:
        return float(
            metric.get("values", {}).get(value_name, default)
        )

    # --summary-export 형식
    return float(metric.get(value_name, default))


def format_duration(milliseconds: float) -> str:
    if milliseconds >= 1000:
        return f"{milliseconds / 1000:.2f}s"

    return f"{milliseconds:.2f}ms"


def format_size(bytes_value: float) -> str:
    if bytes_value >= 1024 * 1024:
        return f"{bytes_value / (1024 * 1024):.2f}MB"

    if bytes_value >= 1024:
        return f"{bytes_value / 1024:.2f}KB"

    return f"{bytes_value:.0f}B"


def calculate_metrics(result: dict[str, Any]) -> dict[str, float]:
    requests = metric_value(result, "http_reqs", "count")
    received = metric_value(result, "data_received", "count")

    return {
        "requests": requests,
        "rps": metric_value(result, "http_reqs", "rate"),
        "avg": metric_value(result, "http_req_duration", "avg"),
        "median": metric_value(result, "http_req_duration", "med"),
        "p95": metric_value(result, "http_req_duration", "p(95)"),
        "max": metric_value(result, "http_req_duration", "max"),
        "failed_rate": metric_value(
            result,
            "http_req_failed",
            "rate",
        ),
        "response_size": received / requests if requests else 0.0,
    }


def mean(values: list[float]) -> float:
    return statistics.mean(values)


def stdev(values: list[float]) -> float:
    if len(values) < 2:
        return 0.0

    return statistics.stdev(values)


def aggregate_runs(
        run_metrics: list[dict[str, float]],
) -> dict[str, float]:
    metric_names = run_metrics[0].keys()

    aggregated: dict[str, float] = {}

    for metric_name in metric_names:
        values = [
            run[metric_name]
            for run in run_metrics
        ]

        aggregated[metric_name] = mean(values)
        aggregated[f"{metric_name}_stdev"] = stdev(values)

    return aggregated


def ratio(numerator: float, denominator: float) -> str:
    if denominator == 0:
        return "-"

    return f"{numerator / denominator:.2f}×"


results: dict[tuple[str, int], dict[str, float]] = {}

for vus in VUS_VALUES:
    for endpoint in ENDPOINTS:
        runs: list[dict[str, float]] = []

        for run in range(1, REPEAT_COUNT + 1):
            raw_result = load_result(endpoint, vus, run)
            runs.append(calculate_metrics(raw_result))

        results[(endpoint, vus)] = aggregate_runs(runs)


vus_text = ", ".join(str(vus) for vus in VUS_VALUES)

lines = [
    "# Problem List Pagination Performance Report",
    "",
    "## Test conditions",
    "",
    "- Environment: EC2",
    "- Duration: 1 minute per test",
    f"- Virtual users: {vus_text}",
    f"- Repetitions: {REPEAT_COUNT} per condition",
    "- Cooldown: 10 seconds between tests",
    "- Think time: 1 second",
    "- Dataset: 10,001 problems",
    "- Legacy API: returns all problems",
    "- Pagination API: returns page 0 with 20 problems",
    "",
    "## Aggregated results",
    "",
    "Each value is the mean of three independent runs.",
    "",
    "| VUs | Endpoint | Requests | Avg latency | Avg SD "
    "| Median | p95 | Max | RPS | Response size | Failure rate |",
    "|---:|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|",
]

for vus in VUS_VALUES:
    for endpoint in ENDPOINTS:
        metric = results[(endpoint, vus)]

        lines.append(
            f"| {vus} "
            f"| {endpoint.capitalize()} "
            f"| {metric['requests']:.1f} "
            f"| {format_duration(metric['avg'])} "
            f"| {format_duration(metric['avg_stdev'])} "
            f"| {format_duration(metric['median'])} "
            f"| {format_duration(metric['p95'])} "
            f"| {format_duration(metric['max'])} "
            f"| {metric['rps']:.2f} "
            f"| {format_size(metric['response_size'])} "
            f"| {metric['failed_rate'] * 100:.2f}% |"
        )

lines.extend(
    [
        "",
        "## Legacy versus pagination",
        "",
        "| VUs | Average latency improvement | p95 improvement "
        "| Response-size reduction | Pagination RPS improvement |",
        "|---:|---:|---:|---:|---:|",
    ]
)

for vus in VUS_VALUES:
    legacy = results[("legacy", vus)]
    paginated = results[("paginated", vus)]

    lines.append(
        f"| {vus} "
        f"| {ratio(legacy['avg'], paginated['avg'])} faster "
        f"| {ratio(legacy['p95'], paginated['p95'])} faster "
        f"| {ratio(legacy['response_size'], paginated['response_size'])} smaller "
        f"| {ratio(paginated['rps'], legacy['rps'])} higher |"
    )

lines.extend(
    [
        "",
        "## Interpretation",
        "",
        "The legacy endpoint reads, converts, serializes, and "
        "transfers all 10,001 problems for every request. "
        "The pagination endpoint returns only 20 problems.",
        "",
        "Therefore, the measured difference includes:",
        "",
        "- Database query cost",
        "- Entity and DTO creation",
        "- JSON serialization",
        "- Server memory usage",
        "- Network transfer",
        "",
        "The RPS results include a one-second think time. "
        "They therefore represent scenario throughput rather than "
        "the maximum throughput of the server.",
        "",
        "Average latency, p95 latency, response size, failure rate, "
        "and the standard deviation across repeated runs should be "
        "considered together.",
        "",
    ]
)

OUTPUT_FILE.write_text(
    "\n".join(lines),
    encoding="utf-8",
)

print(f"Generated report: {OUTPUT_FILE}")