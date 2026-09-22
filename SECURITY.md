# Runtime boundaries

The classroom demo keeps the original automatic Aarya Sharma persona and simulated login/registration. It is shared demonstration data, not a private student account. The existing institution/admin page guards remain in place. The Vercel container deliberately runs this demo mode.

Authenticated mode uses Supabase to verify credentials. Java stores roles and disabled state; Next.js retrieves them before signing service requests. Registration cannot choose an admin role. Java services check roles and ownership for profile changes, application submission, institution decisions, user administration and document records.

The Java server listens on loopback. Requests carry an HMAC over timestamp, nonce, method, path, identity and body hash. Expired, modified or replayed requests are rejected. `AID_API_SECRET` stays server-only and must not be committed.

Scholarship scores are frozen at submission. Duplicate application checks and writes run in the same database transaction. Financial-aid updates retain optimistic version checks and domain-enforced lifecycle rules.

Private uploaded files remain in Supabase Storage under the owner's identifier. Java validates record ownership and compares extracted content with the stored academic profile. Images without extractable text remain pending. The PDF extractor and upload-size validation remain in the Next.js adapter.

H2 files are private local state, not browser assets. The Vercel demo filesystem is ephemeral. The platform repository is designed for one small classroom dataset, not multi-instance production storage. Older Postgres records require a separate migration before enabling this replacement data layer for an existing installation.
