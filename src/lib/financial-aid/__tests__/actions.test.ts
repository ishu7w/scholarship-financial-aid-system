import { beforeEach, describe, expect, it, vi } from "vitest";
const mocks = vi.hoisted(() => ({
  session: vi.fn(),
  java: vi.fn(),
  revalidate: vi.fn(),
}));
vi.mock("@/lib/auth/session", () => ({ getSessionProfile: mocks.session }));
vi.mock("../java-client", () => ({ javaAidRequest: mocks.java }));
vi.mock("next/cache", () => ({ revalidatePath: mocks.revalidate }));
import { changeAid, submitAid, assessAid } from "../actions";
const need = {
  annualIncome: 300000,
  tuition: 120000,
  livingCosts: 60000,
  existingSupport: 30000,
  contribution: 20000,
  emergency: false,
};
const student = { id: "student-1", name: "Test student", role: "student" };
beforeEach(() => {
  vi.resetAllMocks();
  mocks.session.mockResolvedValue(student);
  mocks.java.mockResolvedValue({ ok: true, data: {} });
});
describe("Next.js to Java aid boundary", () => {
  it("blocks signed-out requests", async () => {
    mocks.session.mockResolvedValue(null);
    expect((await submitAid({})).ok).toBe(false);
    expect((await assessAid(need)).ok).toBe(false);
    expect(mocks.java).not.toHaveBeenCalled();
  });
  it("blocks non-student submissions", async () => {
    mocks.session.mockResolvedValue({ ...student, role: "institution" });
    expect((await submitAid({})).ok).toBe(false);
  });
  it("rejects invalid form money before the Java request", async () => {
    expect((await assessAid({ ...need, tuition: -1 })).ok).toBe(false);
    expect(mocks.java).not.toHaveBeenCalled();
  });
  it("forwards assessment to Java", async () => {
    expect((await assessAid(need)).ok).toBe(true);
    expect(mocks.java).toHaveBeenCalledWith(
      student,
      "/api/aid/assess",
      "POST",
      need,
    );
  });
  it("ignores a forged student ID and uses the session", async () => {
    const request = {
      programId: "need-grant",
      need,
      requested: 80000,
      reason: "Tuition assistance requested",
      studentId: "victim",
    };
    expect((await submitAid(request)).ok).toBe(true);
    expect(mocks.java.mock.calls[0][0]).toEqual(student);
    expect(mocks.java.mock.calls[0][3]).not.toHaveProperty("studentId");
  });
  it("requires optimistic version information for review", async () => {
    expect(
      (
        await changeAid({
          id: "c28dab03-9c4c-487f-a04f-d90e580f7b98",
          status: "under_review",
          note: "Review started",
          amount: 0,
        })
      ).ok,
    ).toBe(false);
  });
  it("preserves Java errors without falsely announcing success", async () => {
    mocks.java.mockResolvedValue({ ok: false, error: "Already applied" });
    expect(
      await submitAid({
        programId: "need-grant",
        need,
        requested: 80000,
        reason: "Tuition assistance requested",
      }),
    ).toEqual({ ok: false, error: "Already applied" });
    expect(mocks.revalidate).not.toHaveBeenCalled();
  });
});
