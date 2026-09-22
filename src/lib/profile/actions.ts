"use server";
import { revalidatePath } from "next/cache";
import { javaAction } from "@/lib/java/client";
export type ProfileResult = { ok: true } | { ok: false; error: string };
export async function saveProfileAction(
  input: unknown,
): Promise<ProfileResult> {
  const result = await javaAction<{ ok: true }>("save-profile", input);
  if (result.ok) {
    revalidatePath("/dashboard/student", "layout");
    revalidatePath("/scholarships", "layout");
  }
  return result;
}
