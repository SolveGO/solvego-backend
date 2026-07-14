import http from "k6/http";
import { check } from "k6";

import { environments } from "../config/environment.js";

const environmentName = __ENV.ENV || "local";
const environment = environments[environmentName];

if (!environment) {
    throw new Error(`Unknown environment: ${environmentName}`);
}

const BASE_URL = environment.baseUrl;

export const options = {
    vus: 1,
    iterations: 10,

    thresholds: {
        checks: ["rate==1"],
        http_req_failed: ["rate<0.01"],
        http_req_duration: ["p(95)<500"],
    },
};

export default function () {
    const response = http.get(`${BASE_URL}/api/problems`);

    check(response, {
        "status is 200": (r) => r.status === 200,
    });
}