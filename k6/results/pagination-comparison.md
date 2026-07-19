# Problem List Pagination Performance Report

## Test conditions

- Environment: EC2
- Duration: 1 minute per test
- Virtual users: 1, 5, 10, 20, 30
- Repetitions: 3 per condition
- Cooldown: 10 seconds between tests
- Think time: 1 second
- Dataset: 10,001 problems
- Legacy API: returns all problems
- Pagination API: returns page 0 with 20 problems

## Aggregated results

Each value is the mean of three independent runs.

| VUs | Endpoint | Requests | Avg latency | Avg SD | Median | p95 | Max | RPS | Response size | Failure rate |
|---:|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | Legacy | 32.0 | 1.27s | 1.30s | 892.10ms | 3.13s | 4.48s | 0.53 | 770.45KB | 0.00% |
| 1 | Paginated | 59.0 | 20.75ms | 2.80ms | 18.36ms | 37.25ms | 48.85ms | 0.98 | 1.91KB | 0.00% |
| 5 | Legacy | 174.0 | 752.91ms | 87.10ms | 668.67ms | 1.50s | 2.20s | 2.83 | 770.45KB | 0.00% |
| 5 | Paginated | 293.7 | 27.52ms | 5.04ms | 21.97ms | 45.24ms | 220.93ms | 4.84 | 1.91KB | 0.00% |
| 10 | Legacy | 207.7 | 1.96s | 126.30ms | 1.77s | 4.31s | 5.50s | 3.33 | 770.45KB | 0.00% |
| 10 | Paginated | 590.0 | 23.22ms | 1.58ms | 19.16ms | 43.65ms | 236.97ms | 9.73 | 1.91KB | 0.00% |
| 20 | Legacy | 232.3 | 4.37s | 96.46ms | 3.80s | 9.19s | 14.13s | 3.64 | 770.45KB | 0.00% |
| 20 | Paginated | 1177.0 | 28.40ms | 1.64ms | 21.39ms | 64.55ms | 294.37ms | 19.35 | 1.91KB | 0.00% |
| 30 | Legacy | 202.3 | 8.68s | 1.09s | 7.45s | 18.35s | 28.66s | 3.02 | 770.45KB | 0.00% |
| 30 | Paginated | 1742.3 | 41.17ms | 6.24ms | 24.62ms | 135.88ms | 458.61ms | 25.71 | 1.91KB | 0.00% |

## Legacy versus pagination

| VUs | Average latency improvement | p95 improvement | Response-size reduction | Pagination RPS improvement |
|---:|---:|---:|---:|---:|
| 1 | 61.45× faster | 84.15× faster | 404.38× smaller | 1.86× higher |
| 5 | 27.35× faster | 33.24× faster | 404.38× smaller | 1.71× higher |
| 10 | 84.29× faster | 98.71× faster | 404.38× smaller | 2.92× higher |
| 20 | 153.95× faster | 142.34× faster | 404.38× smaller | 5.32× higher |
| 30 | 210.77× faster | 135.05× faster | 404.38× smaller | 8.51× higher |

## Interpretation

The legacy endpoint reads, converts, serializes, and transfers all 10,001 problems for every request. The pagination endpoint returns only 20 problems.

Therefore, the measured difference includes:

- Database query cost
- Entity and DTO creation
- JSON serialization
- Server memory usage
- Network transfer

The RPS results include a one-second think time. They therefore represent scenario throughput rather than the maximum throughput of the server.

Average latency, p95 latency, response size, failure rate, and the standard deviation across repeated runs should be considered together.
