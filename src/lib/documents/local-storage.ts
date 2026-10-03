import "server-only";
import { mkdir, readdir, rm, writeFile } from "node:fs/promises";
import path from "node:path";
import { tmpdir } from "node:os";

function root() {
  return path.join(process.env.AID_DATA_DIR ?? path.join(tmpdir(), "scholarai-demo"), "documents");
}
function resolveObject(storagePath: string) {
  if (!/^[a-zA-Z0-9_-]+\/[a-zA-Z0-9_.-]+$/.test(storagePath)) throw new Error("Invalid document path");
  return path.join(root(), storagePath);
}

export async function putLocalDocument(storagePath: string, bytes: Uint8Array) {
  const filename = resolveObject(storagePath);
  await mkdir(path.dirname(filename), { recursive: true, mode: 0o700 });
  if ((await readdir(path.dirname(filename))).length >= 25)
    throw new Error("This sample account has 25 documents. Remove an old document before uploading another.");
  await writeFile(filename, bytes, { flag: "wx", mode: 0o600 });
}

export async function removeLocalDocument(storagePath: string) {
  await rm(resolveObject(storagePath), { force: true });
}
