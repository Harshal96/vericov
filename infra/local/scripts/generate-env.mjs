#!/usr/bin/env node
import { randomBytes, randomUUID } from "node:crypto";
import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const scriptDir = dirname(fileURLToPath(import.meta.url));
const localDir = join(scriptDir, "..");
const repoDir = join(localDir, "..", "..");
const supabaseEnvPath = join(repoDir, "infra", "supabase", ".env");
const localEnvPath = join(localDir, ".env");
const force = process.argv.includes("--force");

if (!existsSync(supabaseEnvPath)) {
  console.error("infra/supabase/.env does not exist. Run infra/supabase/scripts/generate-env.mjs first.");
  process.exit(1);
}

if (existsSync(localEnvPath) && !force) {
  console.log("infra/local/.env already exists.");
  process.exit(0);
}

const supabase = readEnvFile(supabaseEnvPath);
const postgresHostPort = supabase.POSTGRES_HOST_PORT || "54322";

const values = {
  BYO_POSTGRES: "1",
  BYO_SUPABASE: "0",
  POSTGRES_PASSWORD: required(supabase, "POSTGRES_PASSWORD"),
  POSTGRES_HOST_PORT: postgresHostPort,
  VERICOV_DB_URL: `jdbc:postgresql://host.docker.internal:${postgresHostPort}/postgres`,
  VERICOV_DB_USER: "postgres",
  VERICOV_DB_PASSWORD: required(supabase, "POSTGRES_PASSWORD"),
  SUPABASE_SERVICE_ROLE_KEY: required(supabase, "SERVICE_ROLE_KEY"),
  SUPABASE_URL: "http://host.docker.internal:8000",
  SUPABASE_STORAGE_URL: "http://host.docker.internal:8000/storage/v1",
  VERICOV_REPO_API_KEY_PEPPER: randomHex(32),
  VERICOV_RUNNER_JWT_SECRET: randomHex(32),
  VERICOV_RUNNER_JWT_ISSUER: "vericov-upload",
  VERICOV_RUNNER_JWT_AUDIENCE: "vericov-runner-upload",
  VERICOV_SERVICE_JWT_PUBLIC_KEY: "",
  VERICOV_SERVICE_JWT_SECRET: randomBase64Url(48),
  VERICOV_SERVICE_JWT_ISSUER: "vericov-gateway",
  VERICOV_SERVICE_JWT_AUDIENCE: "vericov",
  VERICOV_DEV_AUTH_BYPASS: "true",
  VERICOV_DEV_USER_ID: randomUUID(),
  VERICOV_DEV_USER_EMAIL: "dev@vericov.local",
  VERICOV_GITHUB_WEBHOOK_SECRET: randomBase64Url(36),
  VERICOV_CORS_ORIGIN: "*",
  VERICOV_USER_RATE_LIMIT_MINUTE: "120",
  VERICOV_UPLOAD_RATE_LIMIT_MINUTE: "60",
  VERICOV_UPLOAD_RATE_LIMIT_HOUR: "1000",
  VERICOV_UPLOAD_MAX_BODY_MB: "110",
  VERICOV_WEBHOOK_RATE_LIMIT_MINUTE: "120",
  VERICOV_BADGE_RATE_LIMIT_MINUTE: "600",
  VERICOV_INTERNAL_RATE_LIMIT_MINUTE: "120",
  VERICOV_HOST_SUPABASE_URL: "http://localhost:8000",
  VERICOV_HOST_SUPABASE_STORAGE_URL: "http://localhost:8000/storage/v1",
  VERICOV_HOST_SUPABASE_DB_URL: `jdbc:postgresql://localhost:${postgresHostPort}/postgres`,
};

const output = Object.entries(values)
  .map(([key, value]) => `${key}=${value}`)
  .join("\n") + "\n";

writeFileSync(localEnvPath, output, { mode: 0o600 });
console.log("Created infra/local/.env with local-only Vericov service secrets.");

function readEnvFile(path) {
  const values = {};
  for (const line of readFileSync(path, "utf8").split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith("#")) {
      continue;
    }
    const match = trimmed.match(/^([^=]+)=(.*)$/);
    if (match) {
      values[match[1].trim()] = match[2].trim();
    }
  }
  return values;
}

function required(valuesByKey, key) {
  const value = valuesByKey[key];
  if (!value) {
    throw new Error(`${key} is required in infra/supabase/.env`);
  }
  return value;
}

function randomHex(bytes) {
  return randomBytes(bytes).toString("hex");
}

function randomBase64Url(bytes) {
  return randomBytes(bytes).toString("base64url");
}
