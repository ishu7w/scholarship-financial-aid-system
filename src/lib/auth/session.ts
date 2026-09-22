import "server-only";
import { cache } from "react";
import { getSupabaseServer } from "@/lib/supabase/server";
import { isLiveMode } from "@/lib/env";
import { javaHttpRequest } from "@/lib/java/http";

export interface SessionProfile {
  id: string;
  role: "student" | "institution" | "admin";
  name: string;
  email: string;
  avatarHue: number;
}

// Demo persona — mirrors the original hardcoded UI identity.
const DEMO_SESSION: SessionProfile = {
  id: "stu-aarya",
  role: "student",
  name: "Aarya Sharma",
  email: "aarya@university.edu",
  avatarHue: 258,
};

/** Current signed-in profile; demo persona in demo mode; null when
 *  live and signed out. Cached per request. */
export const getSessionProfile = cache(async (): Promise<SessionProfile | null> => {
  if (!isLiveMode()) return DEMO_SESSION;

  const supabase = await getSupabaseServer();
  if (!supabase) return null;
  const { data } = await supabase.auth.getUser();
  if (!data.user) return null;

  const identity: SessionProfile = {
    id: data.user.id, role: "student", name: data.user.email ?? "User",
    email: data.user.email ?? "", avatarHue: 258,
  };
  const result = await javaHttpRequest<SessionProfile & { disabled: boolean } | null>(identity, "/api/platform/account", "POST", {});
  if (!result.ok || !result.data || result.data.disabled) return null;
  return { ...result.data, avatarHue: result.data.avatarHue ?? 258 };
});
