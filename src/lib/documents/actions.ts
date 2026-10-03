import "server-only";

// Private file adapters: temporary disk storage for the demonstration,
// Supabase Storage for real accounts. Java verifies and owns document records.

import { randomUUID } from "node:crypto";
import { isLiveMode } from "@/lib/env";
import { putLocalDocument, removeLocalDocument } from "./local-storage";
import { revalidatePath } from "next/cache";
import { z } from "zod";
import { javaRequest, javaAction } from "@/lib/java/client";
import { getSupabaseAdmin } from "@/lib/supabase/server";
import { getSessionProfile } from "@/lib/auth/session";
import { extractDocumentText, type DocumentKind, type VerificationStatus } from "./verify";

export const DOCUMENTS_BUCKET = "documents";

export const MAX_UPLOAD_BYTES = 5 * 1024 * 1024;

export const ALLOWED_MIME_TYPES = [
  "application/pdf",
  "image/png",
  "image/jpeg",
] as const;

const EXTENSION_BY_MIME: Record<string, string> = {
  "application/pdf": "pdf",
  "image/png": "png",
  "image/jpeg": "jpg",
};

/** Shown in demo mode and whenever storage is not configured. */
export const STORAGE_UNAVAILABLE =
  "Connect storage to upload — file uploads need a Supabase project (NEXT_PUBLIC_SUPABASE_URL + SUPABASE_SERVICE_ROLE_KEY) and the Java backend.";

export const documentKindSchema = z.enum([
  "resume",
  "income_cert",
  "marksheet",
  "id",
]);

/** Form fields accepted by the upload route (the file itself is checked separately). */
export const uploadFieldsSchema = z.object({
  kind: documentKindSchema,
  applicationId: z.uuid().optional().nullable(),
});

export const uploadedFileSchema = z.object({
  name: z.string().min(1),
  size: z
    .number()
    .int()
    .positive("The uploaded file is empty")
    .max(MAX_UPLOAD_BYTES, "File is larger than the 5MB limit"),
  type: z.enum(ALLOWED_MIME_TYPES, {
    error: "Only PDF, PNG and JPEG files are accepted",
  }),
});

/** What the UI is allowed to see. Never includes a URL or the file itself. */
export interface DocumentRecord {
  id: string;
  kind: DocumentKind;
  storagePath: string;
  verificationStatus: VerificationStatus;
  flags: string[];
  extractedFields: Record<string, unknown> | null;
  createdAt: string;
}

export type DocumentActionResult =
  | { ok: true; document: DocumentRecord }
  | { ok: false; error: string };

/** Demo storage is local; real accounts require the private cloud bucket. */
export async function canUpload(): Promise<boolean> {
  return !isLiveMode() || Boolean(getSupabaseAdmin());
}

/**
 * Idempotently ensure the private `documents` bucket exists. Uses the
 * service role — bucket creation is not an anon-key operation. Safe to
 * call on every upload; a pre-existing bucket is not an error.
 */
export async function ensureDocumentsBucket(): Promise<
  { ok: true } | { ok: false; error: string }
> {
  const admin = getSupabaseAdmin();
  if (!admin) return { ok: false, error: STORAGE_UNAVAILABLE };

  const { data, error } = await admin.storage.getBucket(DOCUMENTS_BUCKET);
  if (data && !error) return { ok: true };

  const { error: createError } = await admin.storage.createBucket(DOCUMENTS_BUCKET, {
    public: false,
    fileSizeLimit: MAX_UPLOAD_BYTES,
    allowedMimeTypes: [...ALLOWED_MIME_TYPES],
  });
  // A concurrent request may have won the race — that is success too.
  if (createError && !/exist/i.test(createError.message)) {
    return { ok: false, error: `Could not create the documents bucket: ${createError.message}` };
  }
  return { ok: true };
}

/** Each upload has a unique filename inside the session owner namespace. */
export function buildStoragePath(
  userId: string,
  kind: DocumentKind,
  mimeType: string
): string {
  const ext = EXTENSION_BY_MIME[mimeType] ?? "bin";
  return `${userId}/${kind}-${randomUUID()}.${ext}`;
}

/** Uploads to the private bucket. `userId` must come from the session. */
export async function putDocumentObject(
  userId: string,
  kind: DocumentKind,
  bytes: Uint8Array,
  mimeType: string
): Promise<{ ok: true; storagePath: string } | { ok: false; error: string }> {
  if (!isLiveMode()) {
    const storagePath = buildStoragePath(userId, kind, mimeType);
    try {
      await putLocalDocument(storagePath, bytes);
      return { ok: true, storagePath };
    } catch (error) {
      return { ok: false, error: error instanceof Error ? error.message : "Could not store this sample document" };
    }
  }
  const admin = getSupabaseAdmin();
  if (!admin) return { ok: false, error: STORAGE_UNAVAILABLE };

  const bucket = await ensureDocumentsBucket();
  if (!bucket.ok) return bucket;

  const storagePath = buildStoragePath(userId, kind, mimeType);
  const { error } = await admin.storage
    .from(DOCUMENTS_BUCKET)
    .upload(storagePath, bytes, { contentType: mimeType, upsert: false });

  if (error) return { ok: false, error: `Upload failed: ${error.message}` };
  return { ok: true, storagePath };
}

/**
 * The full upload path: store the file, insert a `pending` row, run the
 * verification pipeline against the student's own DB profile, then
 * update the row with the verdict. Returns the row plus the extracted
 * text so the resume analyzer can score without re-parsing.
 *
 * `userId` and `studentName` must come from the session.
 */
export async function storeAndVerifyDocument(args: {
  userId: string;
  studentName: string;
  kind: DocumentKind;
  applicationId?: string | null;
  bytes: Uint8Array;
  mimeType: string;
}): Promise<
  | { ok: true; document: DocumentRecord; text: string }
  | { ok: false; error: string }
> {
  if (!(await canUpload())) return { ok: false, error: STORAGE_UNAVAILABLE };

  const put = await putDocumentObject(
    args.userId,
    args.kind,
    args.bytes,
    args.mimeType
  );
  if (!put.ok) return put;

  const text = await extractDocumentText(args.bytes, args.mimeType);
  const result = await javaAction<DocumentRecord>("document-save", {
    kind: args.kind, applicationId: args.applicationId ?? null, storagePath: put.storagePath,
    mimeType: args.mimeType, byteSize: args.bytes.byteLength, text,
  });
  if ("ok" in result && !result.ok) {
    if (!isLiveMode()) await removeLocalDocument(put.storagePath);
    else await getSupabaseAdmin()?.storage.from(DOCUMENTS_BUCKET).remove([put.storagePath]);
    return result;
  }
  revalidatePath("/dashboard/student/documents");
  return { ok: true, document: result as DocumentRecord, text };
}

/** Documents owned by the caller. Never call with an id from the client. */
export async function listMyDocuments(): Promise<DocumentRecord[]> {
  const me = await getSessionProfile();
  if (!me) return [];
  return javaRequest<DocumentRecord[]>("documents", { id: me.id });
}

export async function deleteOwnDocument(ownerId: string, documentId: string): Promise<{ ok: true } | { ok: false; error: string }> {
  const me = await getSessionProfile();
  if (!me || me.id !== ownerId) return { ok: false, error: "Sign in to manage your documents" };
  const row = (await listMyDocuments()).find(item => item.id === documentId);
  if (!row) return { ok: false, error: "Document not found" };
  if (!isLiveMode()) {
    try { await removeLocalDocument(row.storagePath); }
    catch { return { ok: false, error: "Could not remove the stored file" }; }
  } else {
    const admin = getSupabaseAdmin();
    if (!admin) return { ok: false, error: STORAGE_UNAVAILABLE };
    const { error } = await admin.storage.from(DOCUMENTS_BUCKET).remove([row.storagePath]);
    if (error) return { ok: false, error: "Could not remove the stored file" };
  }
  const result = await javaAction<{ ok: true }>("document-delete", { id: documentId });
  if (result.ok) revalidatePath("/dashboard/student/documents");
  return result;
}
