"use server";
import { revalidatePath } from "next/cache";
import { z } from "zod";
import { getSessionProfile } from "@/lib/auth/session";
import {
  applicationSchema,
  needSchema,
  type AidRecord,
  type AssessmentResult,
} from "./contracts";
import { javaAidRequest } from "./java-client";

export async function assessAid(input: unknown) {
  const me = await getSessionProfile();
  if (!me)
    return { ok: false as const, error: "Sign in to assess financial aid." };
  const parsed = needSchema.safeParse(input);
  if (!parsed.success)
    return { ok: false as const, error: parsed.error.issues[0].message };
  return javaAidRequest<AssessmentResult>(
    me,
    "/api/aid/assess",
    "POST",
    parsed.data,
  );
}
export async function submitAid(input: unknown) {
  const me = await getSessionProfile();
  if (!me || me.role !== "student")
    return { ok: false, error: "Sign in with a student account to apply." };
  const parsed = applicationSchema.safeParse(input);
  if (!parsed.success)
    return { ok: false, error: parsed.error.issues[0].message };
  const result = await javaAidRequest<AidRecord>(
    me,
    "/api/aid/applications",
    "POST",
    parsed.data,
  );
  if (result.ok) revalidatePath("/financial-aid");
  return result.ok ? { ok: true } : result;
}
const decisionSchema = z.object({
  id: z.string().uuid(),
  status: z.enum([
    "under_review",
    "approved",
    "rejected",
    "disbursed",
    "withdrawn",
  ]),
  note: z.string().trim().min(5).max(1000),
  amount: z.number().int().min(0).max(100000000),
  expectedVersion: z.number().int().positive(),
});
export async function changeAid(input: unknown) {
  const me = await getSessionProfile();
  if (!me) return { ok: false, error: "Sign in to continue." };
  const parsed = decisionSchema.safeParse(input);
  if (!parsed.success)
    return { ok: false, error: parsed.error.issues[0].message };
  const { id, ...decision } = parsed.data;
  const result = await javaAidRequest<AidRecord>(
    me,
    `/api/aid/applications/${id}/transition`,
    "POST",
    decision,
  );
  if (result.ok) revalidatePath("/financial-aid");
  return result.ok ? { ok: true } : result;
}
