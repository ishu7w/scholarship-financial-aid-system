import "server-only";
import type { SessionProfile } from "@/lib/auth/session";
import type { ApplicationStatus } from "@/lib/datasource";
import { javaRequest } from "@/lib/java/client";
export interface CopilotContext {
  mode: "demo" | "live";
  /** The signed-in caller. Answers address this person and no one else. */
  viewer: { id: string; name: string; role: SessionProfile["role"] };
  student: {
    name: string;
    field: string;
    degree: string;
    year: number;
    location: string;
    cgpa: number;
    attendance: number;
    sopQuality: number;
    recommendationStrength: number;
    profileCompletion: number;
    researchPapers: number;
    leadershipRoles: number;
    certifications: number;
    volunteerHours: number;
    skills: string[];
  } | null;
  score: {
    total: number;
    confidence: number;
    strengths: string[];
    gaps: string[];
    components: {
      label: string;
      weightPercent: number;
      raw: number;
      weighted: number;
      detail: string;
    }[];
  } | null;
  matches: {
    scholarshipId: string;
    name: string;
    provider: string;
    category: string;
    amount: number;
    currency: string;
    deadline: string;
    eligible: boolean;
    matchScore: number;
    winProbability: number;
    missingCriteria: string[];
    reasons: string[];
    improvements: string[];
  }[];
  eligibility: { eligibleCount: number; trackedCount: number };
  applications: {
    scholarshipId: string;
    scholarshipName: string;
    status: ApplicationStatus;
    /** Engine output frozen at submit time. Never recomputed. */
    aiSnapshot: {
      total: number;
      confidence: number;
      matchScore: number;
      winProbability: number;
      reasons: string[];
    } | null;
    rejectionReasons: string[];
    submittedAt: string | null;
    decidedAt: string | null;
  }[];
  deadlines: {
    scholarshipId: string;
    name: string;
    deadline: string;
    daysRemaining: number;
    applied: boolean;
  }[];
  roadmap: { quarter: string; title: string; detail: string; impact: number }[];
}

export async function buildCopilotContext(): Promise<CopilotContext> {
  return javaRequest<CopilotContext>("chat-context");
}
