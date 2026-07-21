
const environments = {
    local: {
        baseUrl: "http://localhost:8080",
    },
    ec2: {
        baseUrl: "http://13.124.250.218:8080",
    },
};

export function getBaseUrl() {
    const environmentName = __ENV.ENV || "local";
    const environment = environments[environmentName];

    if (!environment) {
        throw new Error(`Unknown environment: ${environmentName}`);
    }

    return environment.baseUrl;
}