import "server-only";
import { getSessionProfile } from "@/lib/auth/session";
import { javaHttpRequest } from "@/lib/java/http";

/** Next.js handles the browser session; Java owns the application rules. */
export async function javaRequest<T>(
  operation: string,
  input: unknown = {},
): Promise<T> {
  const user = await getSessionProfile();
  const result = await javaHttpRequest<T>(
    user ?? {
      id: "public-catalogue",
      name: "Visitor",
      role: "student",
      email: "",
      avatarHue: 0,
    },
    `/api/platform/${operation}`,
    "POST",
    input,
  );
  if (!result.ok) throw new Error(result.error);
  return result.data;
}

export async function javaAction<T>(
  operation: string,
  input: unknown,
): Promise<T | { ok: false; error: string }> {
  if (!(await getSessionProfile()))
    return { ok: false, error: "Sign in to continue" };
  try {
    return await javaRequest<T>(operation, input);
  } catch (error) {
    return {
      ok: false,
      error:
        error instanceof Error
          ? error.message
          : "Could not complete the request",
    };
  }
}
