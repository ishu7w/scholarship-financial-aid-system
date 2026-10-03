# Runtime boundaries

The classroom demo starts with the Aarya Sharma sample persona and offers explicit Student, Institution and Admin sample accounts. An HTTP-only, same-site cookie selects a fixed sample identity; it is deliberately not real authentication. Signing out sets a signed-out state. Sample role selection is rejected in authenticated mode. Registration and password recovery do not report fake success. The Vercel container deliberately runs this shared demonstration mode. Demo admins may inspect reports but cannot change the fixed sample account roster.

Authenticated mode uses Supabase to verify credentials. Java stores roles and disabled state; Next.js retrieves them before signing service requests. Registration cannot choose an admin role. Java services check roles and ownership for profile changes, application submission, institution decisions, user administration and document records.

The Java server listens on loopback. Requests carry an HMAC over timestamp, nonce, method, path, identity and body hash. Expired, modified or replayed requests are rejected. `AID_API_SECRET` stays server-only and must not be committed.

Scholarship scores are frozen at submission. Duplicate application checks and writes run in the same database transaction. Financial-aid updates retain optimistic version checks and domain-enforced lifecycle rules.

Real-account uploads remain in private Supabase Storage under the owner's identifier. Demo uploads use private local files outside the public directory, unique filenames, path validation and a 25-file per-account limit. Demo documents are shared sample data and reset with the hosted container. Java validates record ownership and compares extracted content with the stored academic profile. Images without extractable text remain pending. The PDF extractor and upload-size validation remain in the Next.js adapter.

H2 files are private local state, not browser assets. The Vercel demo filesystem is ephemeral. The platform repository is designed for one small classroom dataset, not multi-instance production storage. Older Postgres records require a separate migration before enabling this replacement data layer for an existing installation.
