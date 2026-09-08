import { spawn } from "node:child_process";
import { randomBytes } from "node:crypto";
import { existsSync, appendFileSync, chmodSync } from "node:fs";
import { fileURLToPath } from "node:url";
import path from "node:path";

const root = fileURLToPath(new URL("../", import.meta.url));
process.chdir(root);
const envFile = path.join(root, ".env.local");
if (existsSync(envFile)) process.loadEnvFile(envFile);
if (!process.env.AID_API_SECRET) {
  const secret = randomBytes(32).toString("hex");
  appendFileSync(
    envFile,
    `\n# Private server-to-server credential for the Java aid backend\nAID_API_SECRET=${secret}\n`,
    { mode: 0o600 },
  );
  chmodSync(envFile, 0o600);
  process.env.AID_API_SECRET = secret;
}
const live = Boolean(
  process.env.NEXT_PUBLIC_SUPABASE_URL &&
    process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY,
);
const port = process.env.AID_PORT ?? "8080";
const env = {
  ...process.env,
  AID_PORT: port,
  AID_ALLOW_DEMO: live ? "false" : "true",
  AID_DATA_DIR:
    process.env.AID_DATA_DIR ??
    path.join(root, "backend", "data", live ? "live" : "demo"),
  AID_API_URL: `http://127.0.0.1:${port}`,
};
const children = new Set();
let stopping = false;
function stop(code = 0) {
  if (stopping) return;
  stopping = true;
  for (const child of children) child.kill("SIGTERM");
  setTimeout(() => process.exit(code), 300).unref();
}
function launch(command, args, cwd = root) {
  const child = spawn(command, args, { cwd, env, stdio: "inherit" });
  children.add(child);
  child.on("error", (error) => {
    console.error(`${command} could not start: ${error.message}`);
    stop(1);
  });
  child.on("exit", () => children.delete(child));
  return child;
}
process.on("SIGINT", () => stop());
process.on("SIGTERM", () => stop());
const build = launch("mvn", [
  "-q",
  "-f",
  "backend/pom.xml",
  "package",
  "-DskipTests",
]);
const buildCode = await new Promise((resolve) => {
  build.on("exit", resolve);
  build.on("error", () => resolve(1));
});
if (buildCode !== 0) {
  console.error("Java build failed. Install JDK 17+ and Maven, then retry.");
  process.exit(1);
}
const java = launch(
  "java",
  ["-jar", path.join(root, "backend/target/financial-aid-backend-1.0.0.jar")],
  path.join(root, "backend"),
);
java.on("exit", (code) => {
  if (!stopping) stop(code ?? 1);
});
let ready = false;
for (let attempt = 0; attempt < 100 && !stopping; attempt++) {
  try {
    const response = await fetch(`${env.AID_API_URL}/health`, {
      signal: AbortSignal.timeout(1000),
    });
    const result = await response.json();
    if (result.service === "scholarai-java-aid") {
      ready = true;
      break;
    }
  } catch {
    /* The Java process is still starting. */
  }
  await new Promise((resolve) => setTimeout(resolve, 200));
}
if (!ready) {
  console.error(
    "Java backend did not become ready. Check port 8080 and the Java output above.",
  );
  stop(1);
} else {
  const next = launch(process.execPath, [
    path.join(root, "node_modules/next/dist/bin/next"),
    process.argv.includes("--production") ? "start" : "dev",
    ...process.argv.slice(2).filter((arg) => arg !== "--production"),
  ]);
  next.on("exit", (code) => {
    if (!stopping) stop(code ?? 1);
  });
}
