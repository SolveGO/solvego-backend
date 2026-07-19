/*
Run:
  k6 run -e ENV=ec2 -e VUS=1 -e ENDPOINT=paginated k6/tests/baseline.js
  k6 run -e ENV=ec2 -e VUS=1 -e ENDPOINT=legacy k6/tests/baseline.js
*/

import http from "k6/http";
import { check, sleep } from "k6";

import { getBaseUrl } from "../config/environment.js";

const endpoint = __ENV.ENDPOINT || "paginated";

const paths = {
    paginated: "/api/problems?page=0&size=20",
    legacy: "/api/problems/legacy",
};

if (!paths[endpoint]) {
    throw new Error(`Unsupported ENDPOINT: ${endpoint}`);
}

const problemsUrl = `${getBaseUrl()}${paths[endpoint]}`;
const thinkTimeSeconds = 1;
const virtualUsers = Number(__ENV.VUS || 1);

export const options = {
    vus: virtualUsers,
    duration: "1m",

    thresholds: {
        checks: ["rate==1"],
    },
};

export default function baselineLoadTest() {
    const response = http.get(problemsUrl);

    check(response, {
        "returns HTTP 200": ({ status }) => status === 200,
    });

    sleep(thinkTimeSeconds);
}