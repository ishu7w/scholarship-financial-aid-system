# Exact UI reference

Primary reference: https://scholar-ai-rose.vercel.app/ — the website linked in https://github.com/ishu7w/ScholarAI.

Superdesign was used through its authenticated CLI:

- `superdesign search-prompts --query editorial --limit 5 --json` searched its public design library. Library results were references only, not a replacement theme.
- `superdesign extract-website --url https://scholar-ai-rose.vercel.app/ --design-md --tokens --content-structure --out .superdesign/live-reference --json` extracted the actual website. Extraction succeeded and returned a verified design guide called **Dossier Grid**.

The saved design guide, tokens, and structure are in `live-reference/`. The rendered live website and its existing source take precedence over minor inconsistencies in generated descriptions. Older `.superdesign/init/theme.md` describes an earlier dark theme; it is not the current deployed reference.

The existing landing page, navbar, global styles, fonts, original page components, and original animation components are retained from the source clone. Financial Aid is an additional dashboard entry and uses the existing shell, GlassCard, StatPill, badges, premium inputs, chart-free funding form, explorer filters, chat assistant, and the student dashboard's 24px / 0.55s entrance motion. The previously introduced promotional hero was removed.
