import { afterEach, beforeEach, expect, it } from "vitest";
import { mkdtemp, readFile, rm } from "node:fs/promises";
import path from "node:path";
import { tmpdir } from "node:os";
import { putLocalDocument, removeLocalDocument } from "@/lib/documents/local-storage";
let dir: string;
const previous = process.env.AID_DATA_DIR;
beforeEach(async () => { dir = await mkdtemp(path.join(tmpdir(), "scholarai-storage-test-")); process.env.AID_DATA_DIR = dir; });
afterEach(async () => { await rm(dir, { recursive: true, force: true }); if (previous === undefined) delete process.env.AID_DATA_DIR; else process.env.AID_DATA_DIR = previous; });
it("stores documents privately, refuses overwrite, and removes them idempotently", async () => {
  const bytes = new Uint8Array([1, 2, 3]);
  await putLocalDocument("student/sample.pdf", bytes);
  expect(await readFile(path.join(dir, "documents/student/sample.pdf"))).toEqual(Buffer.from(bytes));
  await expect(putLocalDocument("student/sample.pdf", bytes)).rejects.toThrow();
  await removeLocalDocument("student/sample.pdf");
  await removeLocalDocument("student/sample.pdf");
});
it("rejects traversal and caps storage per sample account", async () => {
  await expect(putLocalDocument("../outside", new Uint8Array([1]))).rejects.toThrow("Invalid document path");
  await expect(removeLocalDocument("student/../../outside")).rejects.toThrow("Invalid document path");
  for (let i = 0; i < 25; i++) await putLocalDocument(`student/${i}.pdf`, new Uint8Array([1]));
  await expect(putLocalDocument("student/26.pdf", new Uint8Array([1]))).rejects.toThrow("25 documents");
});
