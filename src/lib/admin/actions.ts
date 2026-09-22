"use server";
import { revalidatePath } from "next/cache";
import { javaRequest, javaAction } from "@/lib/java/client";
import { getSessionProfile } from "@/lib/auth/session";
import { isLiveMode } from "@/lib/env";
export type Role = "student" | "institution" | "admin";

export type AdminActionResult =
  | { ok: true; message?: string }
  | { ok: false; error: string };

export interface PlatformStats {
  mode: "demo" | "live";
  usersByRole: Record<Role, number>;
  totalUsers: number;
  disabledUsers: number;
  scholarshipsByStatus: { draft: number; active: number; closed: number };
  totalScholarships: number;
  applicationsByStatus: {
    draft: number;
    submitted: number;
    under_review: number;
    approved: number;
    rejected: number;
  };
  totalApplications: number;
  decidedApplications: number;
  /** approved / (approved + rejected), null when nothing is decided yet. */
  approvalRate: number | null;
  monthly: { month: string; applications: number; approvals: number }[];
}

export interface AuditEntry {
  id: number;
  actorEmail: string | null;
  action: string;
  entity: string;
  entityId: string;
  createdAt: string;
}

export interface AdminUser {
  id: string;
  name: string;
  email: string;
  role: Role;
  disabled: boolean;
  createdAt: string;
}

export interface AuditPage {
  entries: AuditEntry[];
  total: number;
  page: number;
  pageSize: number;
  /** Distinct actions present in the log — the filter's option list. */
  actions: string[];
  mode: "demo" | "live";
}

async function allowed() {
  const user = await getSessionProfile();
  return user && (!isLiveMode() || user.role === "admin");
}
export async function getPlatformStats(): Promise<PlatformStats | null> {
  return (await allowed()) ? javaRequest<PlatformStats>("admin-stats") : null;
}
export async function getRecentAudit(limit = 6): Promise<AuditEntry[]> {
  const page = await getAuditPage({ page: 1 });
  return page?.entries.slice(0, limit) ?? [];
}
export async function getAuditPage(input: unknown): Promise<AuditPage | null> {
  return (await allowed())
    ? javaRequest<AuditPage>("admin-audit", input)
    : null;
}
export async function listUsers(): Promise<{
  users: AdminUser[];
  mode: "demo" | "live";
} | null> {
  return (await allowed()) ? javaRequest("admin-users") : null;
}
export async function setUserDisabledAction(
  input: unknown,
): Promise<AdminActionResult> {
  const result = await javaAction<{ ok: true }>("admin-disabled", input);
  if (result.ok) revalidatePath("/dashboard/admin", "layout");
  return result;
}
export async function setUserRoleAction(
  input: unknown,
): Promise<AdminActionResult> {
  const result = await javaAction<{ ok: true }>("admin-role", input);
  if (result.ok) revalidatePath("/dashboard/admin", "layout");
  return result;
}
