const viteEnvironment = import.meta.env;
const configuredApiBaseUrl = viteEnvironment?.VITE_API_BASE_URL;

export const API_BASE_URL = configuredApiBaseUrl ??
    (viteEnvironment?.DEV ? "https://localhost:8443" : "");
