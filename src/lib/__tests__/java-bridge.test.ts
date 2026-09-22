import { beforeEach, describe, expect, it, vi } from "vitest";
import { createHash, createHmac } from "node:crypto";
import { javaHttpRequest } from "@/lib/java/http";

const user = {
  id: "student-1",
  name: "Student",
  role: "student" as const,
  email: "student@example.com",
  avatarHue: 258,
};
const secret = "a-private-test-key-at-least-32-characters";

describe("Java transport", () => {
  beforeEach(() => {
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
    vi.stubEnv("AID_API_SECRET", secret);
  });
  it("signs the exact method, path, identity and body sent to Java", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue({
        ok: true,
        json: async () => ({ ok: true, data: { total: 80 } }),
      });
    vi.stubGlobal("fetch", fetchMock);
    expect(
      await javaHttpRequest(user, "/api/platform/score", "POST", {
        student: { id: "student-1" },
      }),
    ).toEqual({ ok: true, data: { total: 80 } });
    const options = fetchMock.mock.calls[0][1];
    const headers = options.headers;
    const payload = [
      headers["X-Aid-Time"],
      headers["X-Aid-Nonce"],
      "POST",
      "/api/platform/score",
      headers["X-Aid-Principal"],
      createHash("sha256").update(options.body).digest("hex"),
    ].join("\n");
    expect(headers["X-Aid-Signature"]).toBe(
      createHmac("sha256", secret).update(payload).digest("hex"),
    );
    expect(
      JSON.parse(
        Buffer.from(headers["X-Aid-Principal"], "base64url").toString(),
      ).id,
    ).toBe(user.id);
    expect(options.cache).toBe("no-store");
  });
  it("preserves Java validation errors", async () => {
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValue({
          ok: false,
          json: async () => ({
            ok: false,
            error: "This scholarship's deadline has passed",
          }),
        }),
    );
    expect(
      await javaHttpRequest(user, "/api/platform/apply", "POST", {
        id: "expired",
      }),
    ).toEqual({ ok: false, error: "This scholarship's deadline has passed" });
  });
  it("reports an unavailable backend instead of inventing results", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new Error("offline")));
    expect(
      (await javaHttpRequest(user, "/api/platform/scholarships", "POST", {})).ok,
    ).toBe(false);
  });
  it("refuses requests when the signing key is missing", async () => {
    vi.stubEnv("AID_API_SECRET", "");
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    expect(
      (await javaHttpRequest(user, "/api/platform/scholarships", "POST", {})).ok,
    ).toBe(false);
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
