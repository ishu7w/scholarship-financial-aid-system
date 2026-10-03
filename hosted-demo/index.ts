// Supabase Edge Function. STORAGE_KEY_SHA256 is generated at setup time;
// the matching secret lives only in Vercel's server environment.
const STORAGE_KEY_SHA256 = Deno.env.get("SCHOLARAI_DEMO_STORAGE_KEY_SHA256");
const headers = { "Content-Type": "application/json" };
const reply = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers });
Deno.serve(async request => {
  if (request.method !== "POST") return reply({ error: "Method not allowed" }, 405);
  const key = request.headers.get("X-Demo-Storage-Key") ?? "";
  const hash = Array.from(new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(key))))
    .map(byte => byte.toString(16).padStart(2, "0")).join("");
  if (hash !== STORAGE_KEY_SHA256) return reply({ error: "Unauthorized" }, 401);
  try {
    const text = await request.text();
    if (text.length > 2_000_000) return reply({ error: "Dataset too large" }, 413);
    const body = JSON.parse(text);
    if (!["scholarships", "financial-aid"].includes(body.dataset)) return reply({ error: "Invalid dataset" }, 400);
    const base = `${Deno.env.get("SUPABASE_URL")}/rest/v1/scholarai_demo_state`;
    const key = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
    const auth = { apikey: key, Authorization: `Bearer ${key}`, "Content-Type": "application/json" };
    if (body.operation === "read") {
      if (!body.seed || typeof body.seed !== "object" || Array.isArray(body.seed)) return reply({ error: "Invalid seed" }, 400);
      const insert = await fetch(base, { method: "POST", headers: { ...auth, Prefer: "resolution=ignore-duplicates" }, body: JSON.stringify({ dataset: body.dataset, payload: body.seed }) });
      if (!insert.ok) return reply({ error: "Storage unavailable" }, 503);
      const read = await fetch(`${base}?dataset=eq.${body.dataset}&select=version,payload`, { headers: auth });
      if (!read.ok) return reply({ error: "Storage unavailable" }, 503);
      const rows = await read.json();
      return rows.length === 1 ? reply(rows[0]) : reply({ error: "Storage unavailable" }, 503);
    }
    if (body.operation === "write" && Number.isSafeInteger(body.version) && body.version > 0 && body.payload && typeof body.payload === "object" && !Array.isArray(body.payload)) {
      const write = await fetch(`${base}?dataset=eq.${body.dataset}&version=eq.${body.version}`, {
        method: "PATCH", headers: { ...auth, Prefer: "return=representation" },
        body: JSON.stringify({ payload: body.payload, version: body.version + 1, updated_at: new Date().toISOString() }),
      });
      if (!write.ok) return reply({ error: "Storage unavailable" }, 503);
      return (await write.json()).length === 1 ? reply({ ok: true }) : reply({ error: "Conflict" }, 409);
    }
    return reply({ error: "Invalid operation" }, 400);
  } catch { return reply({ error: "Storage unavailable" }, 503); }
});
