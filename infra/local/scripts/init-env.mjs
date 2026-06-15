#!/usr/bin/env node
import { randomBytes } from "node:crypto";
import { chmodSync, existsSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const scriptDir = dirname(fileURLToPath(import.meta.url));
const repoDir = resolve(scriptDir, "..", "..", "..");
const templatePath = join(repoDir, ".env.example");
const outputArgument = process.argv.find((argument) => argument.startsWith("--output="));
const outputPath = outputArgument
  ? resolve(process.cwd(), outputArgument.slice("--output=".length))
  : join(repoDir, ".env");
const force = process.argv.includes("--force");

if (existsSync(outputPath) && !force) {
  console.error(`${outputPath} already exists; pass --force to replace it.`);
  process.exit(1);
}

const replacements = new Map([
  ["VERICOV_DB_PASSWORD", randomBase64Url(32)],
  ["VERICOV_DB_JWT_SECRET", randomBase64Url(48)],
  ["VERICOV_REPO_API_KEY_PEPPER", randomHex(32)],
  ["VERICOV_DEV_API_KEY", `vc_repo_${randomHex(20)}`],
  ["VERICOV_RUNNER_JWT_SECRET", randomBase64Url(48)],
]);

const generated = readFileSync(templatePath, "utf8")
  .split(/\r?\n/)
  .map((line) => {
    const separator = line.indexOf("=");
    if (separator < 1) {
      return line;
    }
    const key = line.slice(0, separator);
    return replacements.has(key) ? `${key}=${replacements.get(key)}` : line;
  })
  .join("\n");

writeFileSync(outputPath, generated.endsWith("\n") ? generated : `${generated}\n`, { mode: 0o600 });
chmodSync(outputPath, 0o600);
console.log(`Created ${outputPath} with local-only credentials.`);

function randomHex(bytes) {
  return randomBytes(bytes).toString("hex");
}

function randomBase64Url(bytes) {
  return randomBytes(bytes).toString("base64url");
}
