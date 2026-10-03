import type { SessionProfile } from "./session";

export const DEMO_COOKIE = "scholarai-demo-role";
export const DEMO_ACCOUNTS: Record<SessionProfile["role"], SessionProfile> = {
  student: { id: "stu-aarya", role: "student", name: "Aarya Sharma", email: "student@demo.scholarai.app", avatarHue: 258 },
  institution: { id: "ins-stateuni", role: "institution", name: "State University", email: "institution@demo.scholarai.app", avatarHue: 170 },
  admin: { id: "adm-root", role: "admin", name: "Platform Admin", email: "admin@demo.scholarai.app", avatarHue: 25 },
};

// This selector is used only when real authentication is not configured.
export function demoAccount(role: string | undefined): SessionProfile | null {
  if (role === "signed-out") return null;
  if (role === "institution" || role === "admin") return DEMO_ACCOUNTS[role];
  return DEMO_ACCOUNTS.student;
}
