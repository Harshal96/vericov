#!/usr/bin/env node
import { createHmac, randomBytes } from "node:crypto";
import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const scriptDir = dirname(fileURLToPath(import.meta.url));
const stackDir = join(scriptDir, "..");
const envExamplePath = join(stackDir, ".env.example");
const envPath = join(stackDir, ".env");
const force = process.argv.includes("--force");

if (existsSync(envPath) && !force) {
  console.error("infra/supabase/.env already exists. Re-run with --force to overwrite.");
  process.exit(1);
}

const template = readFileSync(envExamplePath, "utf8");
const nowSeconds = Math.floor(Date.now() / 1000);
const fiveYearsSeconds = 60 * 60 * 24 * 365 * 5;
const jwtSecret = randomHex(32);

const replacements = new Map([
  ["POSTGRES_PASSWORD", randomBase64Url(36)],
  ["JWT_SECRET", jwtSecret],
  ["ANON_KEY", signJwt({ role: "anon", iss: "supabase", iat: nowSeconds, exp: nowSeconds + fiveYearsSeconds }, jwtSecret)],
  ["SERVICE_ROLE_KEY", signJwt({ role: "service_role", iss: "supabase", iat: nowSeconds, exp: nowSeconds + fiveYearsSeconds }, jwtSecret)],
  ["DASHBOARD_PASSWORD", `vc_${randomBase64Url(24)}`],
  ["SECRET_KEY_BASE", randomBase64Url(64)],
  ["VAULT_ENC_KEY", randomBase64Url(24).slice(0, 32)],
  ["PG_META_CRYPTO_KEY", randomBase64Url(24).slice(0, 32)],
  ["S3_PROTOCOL_ACCESS_KEY_ID", randomHex(16)],
  ["S3_PROTOCOL_ACCESS_KEY_SECRET", randomHex(32)],
]);

let output = template;
for (const [key, value] of replacements.entries()) {
  output = output.replace(new RegExp(`^${key}=.*$`, "m"), `${key}=${value}`);
}

writeFileSync(envPath, output, { mode: 0o600 });

console.log("Created infra/supabase/.env with local-only Supabase secrets.");
console.log("Review it before running: cd infra/supabase && docker compose up -d");

function randomHex(bytes) {
  return randomBytes(bytes).toString("hex");
}

function randomBase64Url(bytes) {
  return randomBytes(bytes).toString("base64url");
}

function signJwt(payload, secret) {
  const header = { alg: "HS256", typ: "JWT" };
  const encodedHeader = base64UrlJson(header);
  const encodedPayload = base64UrlJson(payload);
  const signature = createHmac("sha256", secret)
    .update(`${encodedHeader}.${encodedPayload}`)
    .digest("base64url");
  return `${encodedHeader}.${encodedPayload}.${signature}`;
}

function base64UrlJson(value) {
  return Buffer.from(JSON.stringify(value)).toString("base64url");
}
