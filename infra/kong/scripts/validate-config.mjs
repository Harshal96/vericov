import { existsSync, readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const scriptDir = dirname(fileURLToPath(import.meta.url));
const gatewayDir = join(scriptDir, "..");
const repoDir = join(gatewayDir, "..", "..");

const requiredFiles = [
  "kong.yml",
  "docker-compose.yml",
  ".env.example",
  "README.md",
];

const requiredRepoFiles = [
  "infra/docker/helidon-service.Dockerfile",
  "infra/local/docker-compose.yml",
  "infra/local/.env.example",
  "infra/local/scripts/generate-env.mjs",
  "scripts/dev-up.sh",
  "scripts/dev-down.sh",
];

const failures = [];

function check(condition, message) {
  if (!condition) {
    failures.push(message);
  }
}

function readRequired(relativePath) {
  const absolutePath = join(gatewayDir, relativePath);
  check(existsSync(absolutePath), `${relativePath} must exist`);
  return existsSync(absolutePath) ? readFileSync(absolutePath, "utf8") : "";
}

const contents = Object.fromEntries(
  requiredFiles.map((relativePath) => [relativePath, readRequired(relativePath)]),
);

for (const relativePath of requiredRepoFiles) {
  check(existsSync(join(repoDir, relativePath)), `${relativePath} must exist`);
}

const kongConfig = contents["kong.yml"];
const composeConfig = contents["docker-compose.yml"];
const envExample = contents[".env.example"];
const readme = contents["README.md"];
const integrationsConfig = existsSync(join(repoDir, "services/integrations/src/main/resources/application.yaml"))
  ? readFileSync(join(repoDir, "services/integrations/src/main/resources/application.yaml"), "utf8")
  : "";

const requiredKongSnippets = [
  ['_format_version: "3.0"', "uses current Kong declarative config format"],
  ["name: gateway-health", "defines gateway health endpoint"],
  ["return kong.response.exit(200", "terminates health checks inside Kong"],
  ["name: upload-service", "defines upload upstream service"],
  ["url: ${UPLOAD_SERVICE_URL}", "templates upload upstream URL"],
  ["name: upload-api-v1", "defines upload API route"],
  ["- /api/v1/uploads", "routes upload API prefix"],
  ["strip_path: false", "preserves backend API paths"],
  ["name: request-size-limiting", "protects upload ingress from oversized bodies"],
  ["name: rate-limiting", "applies gateway-level throttling"],
  ["consumers:", "defines gateway authentication consumers"],
  ["jwt_secrets:", "defines Supabase JWT verification material"],
  ["key_claim_name: iss", "uses the Supabase JWT issuer claim as the credential key"],
  ["claims_to_verify:", "verifies registered JWT claims at the edge"],
  ["name: jwt", "enforces JWT authentication on user API routes"],
  ["name: request-transformer", "strips spoofable identity headers before upstream forwarding"],
  ["function set_verified_user_headers()", "injects verified user headers after JWT validation"],
  ["function require_authorization_header()", "requires upload credentials before upstream forwarding"],
  ["function require_github_webhook_headers()", "requires GitHub webhook guard headers at the edge"],
  ["name: coverage-analysis-service", "defines coverage-analysis upstream service"],
  ["url: ${COVERAGE_ANALYSIS_SERVICE_URL}", "templates coverage-analysis upstream URL"],
  ["name: coverage-analysis-internal", "defines internal coverage-analysis route"],
  ["- /internal/v1/coverage-analysis", "routes internal coverage-analysis prefix"],
  ["name: ip-restriction", "restricts internal route access"],
  ["name: organization-service", "defines organization upstream service"],
  ["url: ${ORGANIZATION_SERVICE_URL}", "templates organization upstream URL"],
  ["name: organization-api-v1", "defines organization API route"],
  ["- /api/v1/auth", "routes auth API prefix"],
  ["- /api/v1/orgs", "routes organization API prefix"],
  ["name: organization-badge-api-v1", "defines unauthenticated badge route before org JWT routes"],
  ["name: organization-authz-service", "defines internal authz upstream service"],
  ["name: organization-authz-internal", "defines internal authz route"],
  ["- /internal/v1/authz", "routes internal authz prefix"],
  ["name: integrations-service", "defines integrations upstream service"],
  ["url: ${INTEGRATIONS_SERVICE_URL}", "templates integrations upstream URL"],
  ["name: integrations-api-v1", "defines integrations API route"],
  ["- /api/v1/integration-providers", "routes integration provider catalog"],
  ["- /api/v1/integrations", "routes integration connection APIs"],
  ["name: integrations-internal-service", "defines internal integrations upstream service"],
  ["- /internal/v1/integrations", "routes internal integrations APIs"],
  ["name: git-integration-service", "defines Git integration upstream service"],
  ["url: ${GIT_INTEGRATION_SERVICE_URL}", "templates Git integration upstream URL"],
  ["name: git-api-v1", "defines Git API route"],
  ["- /api/v1/git", "routes Git API prefix"],
  ["name: git-webhooks", "defines Git webhook route"],
  ["- /webhooks/github", "routes GitHub webhook prefix"],
  ["name: git-integration-internal-service", "defines internal Git integration upstream service"],
  ["name: git-internal-v1", "defines internal Git route"],
  ["- /internal/v1/git", "routes internal Git prefix"],
  ["name: public-api-not-implemented", "documents planned public API prefixes"],
  ["name: public-api-block", "blocks unknown public API routes by default"],
  ["return kong.response.exit(501", "returns explicit not-implemented responses for future APIs"],
  ["return kong.response.exit(404", "returns not found for blocked route groups"],
  ["name: correlation-id", "emits request correlation IDs"],
  ["name: prometheus", "exposes Kong metrics"],
];

for (const [snippet, reason] of requiredKongSnippets) {
  check(kongConfig.includes(snippet), `kong.yml ${reason}`);
}

const forbiddenSupabaseIngress = ["/auth/v1", "/rest/v1", "/storage/v1"];
for (const pathPrefix of forbiddenSupabaseIngress) {
  check(
    !kongConfig.includes(pathPrefix),
    `product gateway must not proxy Supabase platform path ${pathPrefix}`,
  );
}

const envRefs = [...kongConfig.matchAll(/\$\{([A-Z0-9_]+)\}/g)].map((match) => match[1]);
const uniqueEnvRefs = [...new Set(envRefs)].sort();
const envExampleKeys = new Set(
  envExample
    .split("\n")
    .map((line) => line.trim())
    .filter((line) => line && !line.startsWith("#"))
    .map((line) => line.split("=")[0]),
);

for (const key of uniqueEnvRefs) {
  check(envExampleKeys.has(key), `.env.example must document ${key}`);
}

const requiredComposeSnippets = [
  ['image: kong/kong:', "uses the official Kong Gateway image"],
  ['KONG_DATABASE: "off"', "runs Kong in DB-less mode"],
  ["KONG_DECLARATIVE_CONFIG: /tmp/vericov-kong/kong.yml", "loads generated declarative config"],
  ['${VERICOV_KONG_HTTP_PORT:-9000}:8000', "publishes the product gateway proxy port"],
  ['${VERICOV_KONG_ADMIN_PORT:-9001}:8001', "publishes local-only Kong admin port"],
  ["GIT_INTEGRATION_SERVICE_URL", "configures Git integration upstream URL"],
  ["INTEGRATIONS_SERVICE_URL", "configures integrations upstream URL"],
  ["SUPABASE_JWT_SECRET", "passes Supabase JWT verification secret"],
  ["host.docker.internal:host-gateway", "supports host service upstreams on Linux"],
];

for (const [snippet, reason] of requiredComposeSnippets) {
  check(composeConfig.includes(snippet), `docker-compose.yml ${reason}`);
}

const requiredDocs = [
  "docker compose up -d",
  "http://localhost:9000/healthz",
  "UPLOAD_SERVICE_URL",
  "Supabase",
];

for (const snippet of requiredDocs) {
  check(readme.includes(snippet), `README.md must mention ${snippet}`);
}

check(
  integrationsConfig.includes("port: 8084"),
  "Integrations service must use port 8084 to avoid the Organization service on 8082",
);

if (failures.length > 0) {
  console.error("Kong gateway config validation failed:");
  for (const failure of failures) {
    console.error(`- ${failure}`);
  }
  process.exit(1);
}

console.log("Kong gateway config validation passed.");
