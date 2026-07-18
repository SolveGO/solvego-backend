/*
Run:
  k6 run -e ENV=ec2 -e VUS=1 k6/tests/baseline.js
  k6 run -e ENV=ec2 -e VUS=5 k6/tests/baseline.js
  k6 run -e ENV=ec2 -e VUS=10 k6/tests/baseline.js
*/

import http from "k6/http";
import { check, sleep } from "k6";

import { getBaseUrl } from "../config/environment.js";

const problemsUrl = `${getBaseUrl()}/api/problems`;
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