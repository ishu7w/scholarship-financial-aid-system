import { spawn } from "node:child_process";
import { randomBytes } from "node:crypto";

// Classroom demo: both servers share one ephemeral container and one secret.
// Java stays on loopback; only the Next.js server is publicly reachable.
const env = {
  ...process.env,
  AID_API_SECRET: randomBytes(32).toString("hex"),
  AID_API_URL: "http://127.0.0.1:8080",
  AID_PORT: "8080",
  AID_ALLOW_DEMO: "true",
  AID_DATA_DIR: "/tmp/scholarai-demo",
  PORT: process.env.PORT || "80",
  HOSTNAME: "0.0.0.0",
};
// This image intentionally runs the original no-account demo mode.
delete env.NEXT_PUBLIC_SUPABASE_URL;
delete env.NEXT_PUBLIC_SUPABASE_ANON_KEY;
delete env.DATABASE_URL;
const children = new Set();
let stopping = false;
function stop(code = 0) {
  if (stopping) return;
  stopping = true;
  for (const child of children) child.kill("SIGTERM");
  setTimeout(() => process.exit(code), 1500);
}
function launch(command, args) {
  const child = spawn(command, args, { env, stdio: "inherit" });
  children.add(child);
  child.on("error", () => stop(1));
  child.on("exit", (code) => {
    children.delete(child);
    if (!stopping) stop(code || 1);
  });
}
process.on("SIGTERM", () => stop());
process.on("SIGINT", () => stop());
launch("java", ["-Xmx256m", "-jar", "aid.jar"]);
let ready = false;
for (let attempt = 0; attempt < 100 && !stopping; attempt++) {
  try {
    const response = await fetch(`${env.AID_API_URL}/health`, {
      signal: AbortSignal.timeout(1000),
    });
    if ((await response.json()).service === "scholarai-java-aid") {
      ready = true;
      break;
    }
  } catch { /* Wait for the Java HTTP listener. */ }
  await new Promise((resolve) => setTimeout(resolve, 200));
}
if (ready && !stopping) launch(process.execPath, ["server.js"]);
else stop(1);
