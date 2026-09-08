import { z } from "zod";

// Transport types and form validation only. All eligibility and state rules run in Java.
const money = z.number().int().min(0).max(100_000_000);
export const needSchema = z.object({
  annualIncome: money,
  tuition: money,
  livingCosts: money,
  existingSupport: money,
  contribution: money,
  emergency: z.boolean(),
});
export type NeedInput = z.infer<typeof needSchema>;
export type AidStatus =
  | "submitted"
  | "under_review"
  | "approved"
  | "rejected"
  | "disbursed"
  | "withdrawn";
export type AidRecord = {
  id: string;
  studentId: string;
  studentName: string;
  programId: string;
  requested: number;
  awarded: number;
  status: AidStatus;
  reason: string;
  need: NeedInput;
  createdAt: string;
  updatedAt: string;
  version: number;
  history: { status: AidStatus; note: string; at: string; actor: string }[];
};
export type AidProgram = {
  id: string;
  name: string;
  category: string;
  maximum: number;
  description: string;
};
export type AssessmentResult = {
  gap: number;
  assessments: {
    programId: string;
    eligible: boolean;
    reason: string | null;
    estimate: number;
  }[];
};
export const applicationSchema = z.object({
  programId: z.string().min(1).max(100),
  requested: money.refine((v) => v > 0, "Request an amount greater than zero"),
  reason: z
    .string()
    .trim()
    .min(20, "Explain your need in at least 20 characters")
    .max(2000),
  need: needSchema,
});
