import { beforeEach, describe, expect, it, vi } from "vitest";
const mocks = vi.hoisted(() => ({ set: vi.fn(), isLive: vi.fn(() => false) }));
vi.mock("next/headers", () => ({ cookies: async () => ({ set: mocks.set }) }));
vi.mock("next/navigation", () => ({ redirect: vi.fn() }));
vi.mock("@/lib/env", () => ({ isLiveMode: mocks.isLive }));
vi.mock("@/lib/supabase/server", () => ({ getSupabaseServer: vi.fn(), getSupabaseAdmin: vi.fn() }));
vi.mock("@/lib/auth/session", () => ({ getSessionProfile: vi.fn() }));
import { enterDemoAction, signInAction, signUpAction, resetPasswordAction } from "@/lib/auth/actions";
import { demoAccount } from "@/lib/auth/demo";

describe("demonstration authentication", () => {
  beforeEach(() => { vi.clearAllMocks(); mocks.isLive.mockReturnValue(false); });
  it("opens all three sample roles with an HTTP-only cookie", async () => {
    for (const role of ["student", "institution", "admin"]) {
      expect(await enterDemoAction(role)).toEqual({ ok: true, redirectTo: `/dashboard/${role}` });
      expect(mocks.set).toHaveBeenLastCalledWith("scholarai-demo-role", role, expect.objectContaining({ httpOnly: true, sameSite: "lax" }));
      expect(demoAccount(role)?.role).toBe(role);
    }
    expect(demoAccount("signed-out")).toBeNull();
  });
  it("cannot activate sample roles when real authentication is enabled", async () => {
    mocks.isLive.mockReturnValue(true);
    expect((await enterDemoAction("admin")).ok).toBe(false);
    expect(mocks.set).not.toHaveBeenCalled();
  });
  it("rejects unknown roles and incorrect demo credentials", async () => {
    expect((await enterDemoAction("owner")).ok).toBe(false);
    expect((await signInAction({ email: "student@demo.scholarai.app", password: "wrong" })).ok).toBe(false);
    expect(mocks.set).not.toHaveBeenCalled();
    expect(await signInAction({ email: "institution@demo.scholarai.app", password: "scholarai-demo" })).toEqual({ ok: true, redirectTo: "/dashboard/institution" });
  });
  it("does not claim that a demo account or reset email was created", async () => {
    expect((await signUpAction({ role: "student", name: "Sample Student", email: "sample@example.com", password: "sample-password" })).ok).toBe(false);
    expect((await resetPasswordAction("sample@example.com")).ok).toBe(false);
  });
});
