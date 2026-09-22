import "server-only";
import { javaRequest } from "@/lib/java/client";
import { isLiveMode } from "@/lib/env";
import type { Scholarship, StudentProfile } from "@/lib/types";
import type {
  ApplicationRecord,
  DataSource,
  DocumentSummary,
  InstitutionAggregates,
  InstitutionApplicant,
  InstitutionRecord,
  InstitutionScholarship,
} from "./index";
import { getSessionProfile } from "@/lib/auth/session";

export class JavaDataSource implements DataSource {
  readonly mode = isLiveMode() ? ("live" as const) : ("demo" as const);
  getScholarships() {
    return javaRequest<Scholarship[]>("scholarships");
  }
  getScholarship(id: string) {
    return javaRequest<Scholarship | null>("scholarship", { id });
  }
  async getStudentProfile(id?: string) {
    const user = await getSessionProfile();
    if (!user || user.role !== "student") return null;
    return javaRequest<StudentProfile | null>("student", { id: id ?? user.id });
  }
  getApplications(id: string) {
    return javaRequest<ApplicationRecord[]>("applications", { id });
  }
  async getApplication(studentId: string, scholarshipId: string) {
    return (
      (await this.getApplications(studentId)).find(
        (a) => a.scholarshipId === scholarshipId,
      ) ?? null
    );
  }
  getSavedScholarshipIds(id: string) {
    return javaRequest<string[]>("saved", { id });
  }
  getInstitutionForProfile(id: string) {
    return javaRequest<InstitutionRecord | null>("institution", { id });
  }
  getInstitutionApplicants(id: string) {
    return javaRequest<InstitutionApplicant[]>("institution-applicants", {
      id,
    });
  }
  getInstitutionScholarships(id: string) {
    return javaRequest<InstitutionScholarship[]>("institution-scholarships", {
      id,
    });
  }
  async getInstitutionScholarship(
    institutionId: string,
    scholarshipId: string,
  ) {
    return (
      (await this.getInstitutionScholarships(institutionId)).find(
        (s) => s.id === scholarshipId,
      ) ?? null
    );
  }
  getInstitutionAggregates(id: string) {
    return javaRequest<InstitutionAggregates>("institution-aggregates", { id });
  }
  async getDocuments(id: string): Promise<DocumentSummary[]> {
    const rows = await javaRequest<DocumentSummary[]>("documents", { id });
    return rows.map(
      ({
        id,
        kind,
        verificationStatus,
        flags,
        extractedFields,
        createdAt,
      }) => ({
        id,
        kind,
        verificationStatus,
        flags,
        extractedFields,
        createdAt,
      }),
    );
  }
}
