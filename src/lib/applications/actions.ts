"use server";
import { revalidatePath } from "next/cache";
import { javaAction } from "@/lib/java/client";
export type ActionResult = { ok: true } | { ok: false; error: string };
async function change(operation: string, id: string): Promise<ActionResult> {
  const result = await javaAction<{ ok: true }>(operation, { id });
  if (result.ok) {
    revalidatePath("/dashboard/student");
    revalidatePath("/dashboard/institution");
    revalidatePath("/scholarships");
    revalidatePath(`/scholarships/${id}`);
  }
  return result;
}
export async function applyAction(id: string) {
  return change("apply", id);
}
export async function withdrawAction(id: string) {
  return change("withdraw", id);
}
export async function toggleSaveAction(id: string) {
  const result = await javaAction<{ ok: true; saved: boolean }>("toggle-save", {
    id,
  });
  if (result.ok) revalidatePath("/scholarships");
  return result;
}
