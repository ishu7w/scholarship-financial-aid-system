import "server-only";

// External-file adapter only. Java owns document parsing and verification rules.

export type DocumentKind = "resume" | "income_cert" | "marksheet" | "id";
export type VerificationStatus = "pending" | "verified" | "flagged";

/** A parsed money figure and the line it came from. */
export interface AmountCandidate {
  value: number;
  raw: string;
  line: string;
  /** The source line mentions income/salary — a labelled figure beats a stray number. */
  labelled: boolean;
}

/** A parsed academic result normalised onto the profile's 10-point scale. */
export interface ScoreCandidate {
  /** Value on a 0–10 scale, comparable to student_profiles.cgpa. */
  normalized: number;
  raw: string;
  line: string;
  basis: "cgpa" | "cgpa-ratio" | "percentage";
  /** Stated when the source scale was inferred rather than printed. */
  assumedScale: string | null;
}

export interface VerificationInput {
  kind: DocumentKind;
  mimeType: string;
  bytes: Uint8Array;
  studentName: string;
  /** student_profiles.family_income — null when the student hasn't declared one. */
  declaredIncome: number | null;
  /** student_profiles.cgpa — null when the student hasn't declared one. */
  declaredCgpa: number | null;
}

export interface VerificationOutcome {
  status: VerificationStatus;
  flags: string[];
  extractedFields: Record<string, unknown>;
  /** Extracted text, reused by the resume analyzer so we parse once. */
  text: string;
}

// ---------- text extraction ----------

/**
 * Text layer of a PDF, or "" for anything without one (images, scans,
 * corrupt files). Never throws — an unreadable file is a `pending`
 * verification, not a request failure.
 */
export async function extractDocumentText(
  bytes: Uint8Array,
  mimeType: string
): Promise<string> {
  if (mimeType === "text/plain" || mimeType === "text/markdown") {
    return new TextDecoder().decode(bytes).trim();
  }
  if (mimeType !== "application/pdf") return "";

  try {
    const { extractText } = await import("unpdf");
    // Copy: pdf.js transfers/detaches the buffer it is handed.
    const { text } = await extractText(new Uint8Array(bytes), { mergePages: true });
    return text.trim();
  } catch {
    return "";
  }
}
