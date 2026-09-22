import "server-only";
import { javaRequest } from "@/lib/java/client";
import type {
  AIScore,
  FraudReport,
  MatchResult,
  Scholarship,
  StudentProfile,
} from "./types";
import type { ResumeAnalysis, RoadmapItem } from "./engine-contracts";
export type { ResumeAnalysis, RoadmapItem } from "./engine-contracts";

export function computeAIScore(student: StudentProfile) {
  return javaRequest<AIScore>("score", { student });
}
export function matchScholarship(
  student: StudentProfile,
  scholarship: Scholarship,
) {
  return javaRequest<MatchResult>("match", { student, scholarship });
}
export function rankScholarships(
  student: StudentProfile,
  scholarships: Scholarship[],
) {
  return javaRequest<MatchResult[]>("rank", { student, scholarships });
}
export function detectFraud(student: StudentProfile) {
  return javaRequest<FraudReport>("fraud", { student });
}
export function generateRoadmap(student: StudentProfile) {
  return javaRequest<RoadmapItem[]>("roadmap", { student });
}
export function analyzeResume(text: string) {
  return javaRequest<ResumeAnalysis>("resume", { text });
}
