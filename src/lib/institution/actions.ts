"use server";
import { revalidatePath } from "next/cache";
import { javaAction } from "@/lib/java/client";
export type ActionResult = { ok: true } | { ok: false; error: string };
export type CreateResult =
  | { ok: true; id: string }
  | { ok: false; error: string };
function refresh() {
  revalidatePath("/dashboard/institution", "layout");
  revalidatePath("/dashboard/student", "layout");
  revalidatePath("/scholarships", "layout");
  revalidatePath("/catalogue");
}
export async function decideApplicationAction(
  input: unknown,
): Promise<ActionResult> {
  const result = await javaAction<{ ok: true }>("decide", input);
  if (result.ok) refresh();
  return result;
}
export async function createScholarshipAction(
  input: unknown,
): Promise<CreateResult> {
  const result = await javaAction<{ ok: true; id: string }>(
    "create-scholarship",
    input,
  );
  if (result.ok) refresh();
  return result;
}
export async function updateScholarshipAction(
  input: unknown,
): Promise<ActionResult> {
  const id =
    typeof input === "object" && input !== null && "id" in input
      ? input.id
      : "";
  const result = await javaAction<{ ok: true }>("update-scholarship", {
    id,
    data: input,
  });
  if (result.ok) refresh();
  return result;
}
