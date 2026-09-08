import "server-only";
import { createHash, createHmac, randomUUID } from "node:crypto";
import type { SessionProfile } from "@/lib/auth/session";
import { isLiveMode } from "@/lib/env";

export async function javaAidRequest<T>(
  user: SessionProfile,
  path: string,
  method = "GET",
  data?: unknown,
): Promise<{ ok: true; data: T } | { ok: false; error: string }> {
  const secret = process.env.AID_API_SECRET;
  if (!secret || secret.length < 32)
    return {
      ok: false,
      error:
        "Java backend is not configured. Start the project with npm run dev.",
    };
  const body = data === undefined ? "" : JSON.stringify(data);
  const timestamp = String(Math.floor(Date.now() / 1000));
  const nonce = randomUUID();
  const principal = Buffer.from(
    JSON.stringify({
      id: user.id,
      name: user.name,
      role: user.role,
      demo: !isLiveMode(),
    }),
  ).toString("base64url");
  const digest = createHash("sha256").update(body).digest("hex");
  const signature = createHmac("sha256", secret)
    .update([timestamp, nonce, method, path, principal, digest].join("\n"))
    .digest("hex");
  try {
    const response = await fetch(
      `${process.env.AID_API_URL ?? "http://127.0.0.1:8080"}${path}`,
      {
        method,
        body: body || undefined,
        cache: "no-store",
        signal: AbortSignal.timeout(10000),
        headers: {
          "Content-Type": "application/json",
          "X-Aid-Time": timestamp,
          "X-Aid-Nonce": nonce,
          "X-Aid-Principal": principal,
          "X-Aid-Signature": signature,
        },
      },
    );
    const result = await response.json();
    if (!response.ok || result.ok !== true)
      return {
        ok: false,
        error:
          typeof result.error === "string"
            ? result.error
            : "Java backend could not complete the request.",
      };
    return { ok: true, data: result.data as T };
  } catch {
    return {
      ok: false,
      error:
        "Java backend is unavailable. Start both services with npm run dev, then retry.",
    };
  }
}
