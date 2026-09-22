import { existsSync } from "node:fs";
import { createHash, createHmac, randomUUID } from "node:crypto";

if (existsSync(".env.local")) process.loadEnvFile(".env.local");
const base = process.env.AID_API_URL ?? "http://127.0.0.1:8080";
const secret = process.env.AID_API_SECRET;
if (!secret || secret.length < 32)
  throw new Error(
    "Start npm run dev once to create the private Java signing key.",
  );
const health = await fetch(`${base}/health`).then((response) =>
  response.json(),
);
if (health.ok !== true) throw new Error("Java backend is not healthy");
const path = "/api/platform/scholarships";
const body = "{}";
const timestamp = String(Math.floor(Date.now() / 1000));
const nonce = randomUUID();
const principal = Buffer.from(
  JSON.stringify({
    id: "public-catalogue",
    name: "Visitor",
    role: "student",
    demo: false,
  }),
).toString("base64url");
const signature = createHmac("sha256", secret)
  .update(
    [
      timestamp,
      nonce,
      "POST",
      path,
      principal,
      createHash("sha256").update(body).digest("hex"),
    ].join("\n"),
  )
  .digest("hex");
const response = await fetch(`${base}${path}`, {
  method: "POST",
  body,
  headers: {
    "Content-Type": "application/json",
    "X-Aid-Time": timestamp,
    "X-Aid-Nonce": nonce,
    "X-Aid-Principal": principal,
    "X-Aid-Signature": signature,
  },
});
const result = await response.json();
if (!response.ok || result.ok !== true || !Array.isArray(result.data))
  throw new Error("Signed Java catalogue request failed");
console.log(
  `Java backend healthy; signed API accepted; ${result.data.length} active scholarships available.`,
);
