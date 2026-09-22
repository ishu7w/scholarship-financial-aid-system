// Frontend response contracts. JavaDataSource forwards reads to the Java modules.

import "server-only";
import type { AIScore, Scholarship, StudentProfile } from "@/lib/types";


export type ApplicationStatus =
  | "draft"
  | "submitted"
  | "under_review"
  | "approved"
  | "rejected";

export interface ApplicationRecord {
  id: string;
  scholarshipId: string;
  studentId: string;
  status: ApplicationStatus;
  /** Engine output frozen at submit time — the audit trail. */
  aiSnapshot: {
    total: number;
    confidence: number;
    matchScore: number;
    winProbability: number;
    components: AIScore["components"];
    reasons: string[];
  } | null;
  rejectionReasons: string[];
  submittedAt: string | null;
  decidedAt: string | null;
}

export interface InstitutionRecord {
  id: string;
  profileId: string;
  orgName: string;
  verified: boolean;
}

export type ScholarshipStatus = "draft" | "active" | "closed";

export interface InstitutionScholarship extends Scholarship {
  status: ScholarshipStatus;
}

/**
 * One application to a scholarship this institution owns, joined with the
 * applicant's academic profile. `aiSnapshot` is the engine output FROZEN at
 * submit time — the audit trail; it is never recomputed over.
 */
export interface InstitutionApplicant {
  applicationId: string;
  scholarshipId: string;
  scholarshipName: string;
  scholarshipCategory: string;
  status: ApplicationStatus;
  aiSnapshot: ApplicationRecord["aiSnapshot"];
  rejectionReasons: string[];
  submittedAt: string | null;
  decidedAt: string | null;
  student: StudentProfile;
  documentsVerified: number;
  documentsTotal: number;
}

/** SQL aggregates over this institution's applications. Empty arrays are
 *  honest: they mean "no rows yet", never a placeholder series. */
export interface InstitutionAggregates {
  /** Grouped by month of submitted_at (chronological, last 12 buckets). */
  monthly: { month: string; applications: number; approvals: number }[];
  /** Grouped by the applied-to scholarship's category. */
  categories: { name: string; value: number }[];
  /** Grouped by the applicant's declared location. */
  regions: { region: string; students: number; funded: number }[];
}

/** A document's verification outcome. Deliberately has no storage path or
 *  URL: institutions read these results, never the raw files. */
export interface DocumentSummary {
  id: string;
  kind: "resume" | "income_cert" | "marksheet" | "id";
  verificationStatus: "pending" | "verified" | "flagged";
  flags: string[];
  extractedFields: Record<string, unknown> | null;
  createdAt: string;
}

export interface DataSource {
  readonly mode: "demo" | "live";
  getScholarships(): Promise<Scholarship[]>;
  getScholarship(id: string): Promise<Scholarship | null>;
  /** Demo mode: the seeded demo student. Live mode: the given profile id. */
  getStudentProfile(profileId?: string): Promise<StudentProfile | null>;
  getApplications(studentId: string): Promise<ApplicationRecord[]>;
  getApplication(
    studentId: string,
    scholarshipId: string
  ): Promise<ApplicationRecord | null>;
  getSavedScholarshipIds(studentId: string): Promise<string[]>;
  // ── institution surface (Phase 4) ───────────────────────────
  getInstitutionForProfile(profileId: string): Promise<InstitutionRecord | null>;
  getInstitutionApplicants(institutionId: string): Promise<InstitutionApplicant[]>;
  getInstitutionScholarships(institutionId: string): Promise<InstitutionScholarship[]>;
  getInstitutionScholarship(
    institutionId: string,
    scholarshipId: string
  ): Promise<InstitutionScholarship | null>;
  getInstitutionAggregates(institutionId: string): Promise<InstitutionAggregates>;
  // ── documents surface (Phase 3) ─────────────────────────────
  /** Verification results only — storage paths never leave the server. */
  getDocuments(studentId: string): Promise<DocumentSummary[]>;
}

export async function getDataSource(): Promise<DataSource> {
  const { JavaDataSource } = await import("./java");
  return new JavaDataSource();
}
