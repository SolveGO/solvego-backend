import http from 'k6/http';
import { check } from 'k6';
import { Rate } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL;
const PAGE = __ENV.PAGE || '0';
const SIZE = __ENV.SIZE || '20';

const errorRate = new Rate('problem_list_error_rate');

export const options = {
    vus: Number(__ENV.VUS || 20),
    duration: __ENV.DURATION || '30s',

    thresholds: {
        http_req_failed: ['rate<0.01'],
        problem_list_error_rate: ['rate<0.01'],
        http_req_duration: ['p(95)<500'],
    },
};

export default function () {
    const url = `${BASE_URL}/api/problems?page=${PAGE}&size=${SIZE}`;

    const response = http.get(url);

    const success = check(response, {
        'status is 200': (res) => res.status === 200,
    });

    errorRate.add(!success);
}