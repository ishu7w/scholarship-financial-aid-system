---
version: "superdesign-alpha"
name: "Dossier Grid"
description: "Bureaucratic-brutalist light system on a warm paper field — hard-edged rectangles, monospace body copy, and a rationed rust-red accent standing in for every signal of urgency or emphasis."
colors:
  background: "#E8E6DF"
  surface: "#DEDBD2"
  surface-2: "#E2DFD7"
  text-primary: "#151515"
  text-secondary: "#75736C"
  accent: "#C4442C"
  accent-secondary: "#A53A24"
  border: "#131313"
typography:
  display-lg:
    fontFamily: "Familjen Grotesk"
    fontSize: "60px"
    fontWeight: 700
    lineHeight: "1.08"
    letterSpacing: "-1.8px"
  headline-md:
    fontFamily: "Familjen Grotesk"
    fontSize: "48px"
    fontWeight: 700
    lineHeight: "1"
    letterSpacing: "-1.4px"
  body-md:
    fontFamily: "Spline Sans Mono"
    fontSize: "18px"
    fontWeight: 400
    lineHeight: "1.63"
  label-md:
    fontFamily: "Familjen Grotesk"
    fontSize: "14px"
    fontWeight: 600
    lineHeight: "1.43"
    letterSpacing: "-0.4px"
  body-sm-mono:
    fontFamily: "Spline Sans Mono"
    fontSize: "14px"
    fontWeight: 400
    lineHeight: "1.5"
spacing:
  base: "8px"
  gap: "24px"
  section-padding: "96px"
rounded:
  control: "0px"
  card: "0px"
  pill: "0px"
components:
  button-nav-cta:
    background: "#151515"
    text-color: "#E8E6DF"
    radius: "0px"
    height: "34px"
  button-primary:
    background: "#151515"
    text-color: "#E8E6DF"
    radius: "0px"
    height: "56px"
    padding: "0px"
    hover-background: "#494844"
  button-secondary-filled:
    background: "#151515"
    text-color: "#E8E6DF"
    radius: "0px"
    height: "47px"
    padding: "14px 24px"
    border: "1px solid rgb(21, 21, 21)"
    hover-background: "#494844"
  button-outline:
    background: "transparent"
    text-color: "#151515"
    radius: "0px"
    height: "47px"
    padding: "14px 24px"
    border: "1px solid rgba(21, 21, 21, 0.16)"
  button-ghost-row:
    background: "transparent"
    text-color: "#151515"
    radius: "0px"
    height: "61px"
    padding: "20px 24px"
  card-feature:
    background: "#DEDBD2"
    radius: "0px"
    padding: "24px"
  card-panel-full:
    background: "transparent"
    radius: "0px"
    padding: "96px 0px"
  card-faq-row:
    background: "#DEDBD2"
    radius: "0px"
    padding: "24px"
  card-pricing:
    background: "#DEDBD2"
    radius: "0px"
    padding: "32px"
  card-testimonial:
    background: "#DEDBD2"
    radius: "0px"
    padding: "24px"
---
# Dossier Grid
Source: https://scholar-ai-rose.vercel.app/

## Overview
This is a light-mode, brutalist-adjacent system: a warm paper-toned page (`#E8E6DF`) filled with hard-edged, zero-radius rectangles, ruled borders, and a monospace body voice (Spline Sans Mono) that reads like a technical report rather than a marketing surface. Display type is a heavy grotesque (Familjen Grotesk, 700 weight, tight negative tracking) used at oversized scale for headlines, giving it a stamped, institutional confidence — closer to Swiss/International rigor crossed with neobrutalist flatness (thick square buttons, visible 1px borders, zero shadows) than to any soft SaaS gradient style. A single rust-red (`#C4442C`/`#A53A24`) is rationed to labels, numerals, and one decorative diagonal-diamond scatter — everything else stays ink-on-paper monochrome.

## Composition
The first screen is left-loaded: an eyebrow line in accent red, a four-line stacked headline (the third line rendered in an outlined/ghost weight of the same type as a deliberate variation), a mono paragraph, two buttons, and a small proof-stat row — all confined to roughly the left half. The right half carries a diagrammatic illustration: a black square hub labeled "AI," dashed orbital rings, floating diamond glyphs, and three flat data-readout cards. This is a deliberate asymmetric split (content left, generative diagram right) rejecting a centered hero — it lets the page read as a dashboard preview rather than a poster. Below the fold, rhythm alternates between a dense stat strip (4-up numerals), a numbered vertical step list, an 8-up feature grid, a 3-up testimonial row, an accordion FAQ, and a 3-up pricing block, closing on a boxed CTA panel and a wide footer. Density is high and even — nearly every section keeps the same 24/48/96px spacing scale, no section breathes dramatically more than another.

## Colors
Background `#E8E6DF` and card surface `#DEDBD2` (with a near-identical `#E2DFD7` tier) carry ~94% of the page — a warm off-white duo distinguished only by a few percent of value, giving elevation through faint tonal shift rather than shadow. Ink `#151515` is both text and the fill for every solid button and the AI-hub square — it functions as the de facto "primary" color despite being neutral. Rust `#C4442C`/`#A53A24` is the only hue in the system: it marks eyebrows, live numerals (match score, stat figures), the "most popular" pricing tag, quote marks, and the diamond accents in the hero diagram — a strict labeling/emphasis role, never a fill. Muted `#75736C` carries all secondary/body-supporting text. Nothing is tinted or gradient-washed; the palette is deliberately kept to ink, paper, and one warning-red accent.

## Typography
Familjen Grotesk carries all display and label duty: 60px/700/-1.8px for the hero, 48px/700/-1.4px for section headlines, and 14px/600/-0.4px for small caps-style labels and eyebrows. Spline Sans Mono is the body workhorse at 18px/400/1.63 for lead paragraphs and drops to 14px/400 for card copy and captions — its monospace grid reinforces the dossier/ledger feel and is the system's true signature typeface (used far more than the display face by character count). Hierarchy is purely a size/weight contrast between the two families, with no italics or serif accents anywhere.

## Layout
Content is bound to a 1152px max-width container with 96px section padding and a base spacing scale of 8/12/16/24/48px. Card grids include: a 2-column split at the hero-diagram row (46/42 widths); a 3-column, 3-up row for testimonials (32/32/32); and an 8-item feature grid arranged as two full rows of four (24/24/24/24 twice) with 20–24px gaps. FAQ and pricing sections stack full-width single-column rows (100% each). A faint 1px-line graph-paper grid (`linear-gradient(rgba(21,21,21,0.05) 1px, transparent 1px)` in both axes) underlies the entire page, reinforcing the technical-grid read at all breakpoints. All cards are zero-radius, so the grid always reads as sharp, ruled rectangles rather than soft tiles.

## Components
- **Navbar** — edge-to-edge, full 100% viewport width, 0/0/0/0 corner radii (square, not inset or capsule), 76px tall, transparent background, sticky on scroll; holds a logo mark (black squircle badge + wordmark, red "AI" suffix) plus 10 items total (6 nav links, Sign in, and the filled CTA). CTA: `#151515` fill, `#E8E6DF` text, 0px radius, 34px height — small and dense compared to hero buttons.
- **Hero primary button** — the solid black rectangle beneath the headline (observed ~0px radius, ~56px height, no padding declared, full ink fill `#151515` / `#E8E6DF` text): this is the single most emphasized control on the first screen, hover shifts fill to `#494844`.
- **Hero secondary button** — an outline variant beside it: transparent fill, `#151515` text, 1px solid `rgba(21,21,21,0.16)` border, 0px radius, 47px height, 14px 24px padding — a lighter-weight companion to the primary, not a competing CTA.
- **Filled utility button** (mid-page CTA band, e.g. footer callout) — `#151515`/`#E8E6DF`, 0px radius, 47px height, 14px 24px padding, 1px solid border matching fill; hover → `#494844` with a subtle 0.14px transform nudge.
- **Ghost row button** — used ×5 in list/step contexts: transparent fill, `#151515` text, 0px radius, tall 61px height, 20px 24px padding — reads as a full-width clickable row rather than a discrete button.
- **Stat strip cards** (×4, one row) — `#DEDBD2` fill, 0px radius, oversized red numeral top, mono caption below; sit directly under the hero as the first proof band.
- **Numbered step list** (×3–5, stacked full-width) — small square unchecked-checkbox glyph + red two-digit index, bold headline, mono body paragraph; anatomy is icon + expandable row + body-text, one per row at 100% width.
- **Feature grid cards** (×8, two rows of four) — `#DEDBD2` fill, 0px radius, no padding declared at container level (content self-pads ~24px); each holds a small red-tinted icon glyph, a bold uppercase-style heading, and 2–3 lines of mono body copy.
- **Testimonial cards** (×3 in a row) — `#DEDBD2` fill, 0px radius, 24px padding; anatomy: large red quotation-mark glyph, mono quote paragraph, then a bordered square initials avatar plus bold name and muted role/affiliation line.
- **FAQ accordion rows** (×5, stacked full-width) — `#DEDBD2`/`#E2DFD7` fill, 0px radius, bold question line with a chevron, expanded row reveals a mono answer paragraph; only one row shown expanded at a time.
- **Pricing cards** (×3 in a row) — `#DEDBD2` fill, 0px radius, 32px padding; anatomy: plan-name label, oversized price numeral with mono "/month" suffix, a "most popular" red-outlined pill tag on the center card only, feature checklist, and a bottom CTA button (filled black on the featured plan, outlined on the others).
- **Closing CTA panel** — a single boxed card (not full-bleed) holding a two-line headline (second line in accent red), a muted mono subline, and one centered filled black button; the only place a soft radial color wash appears behind the panel corner.
- **Footer** — transparent background, top hairline `1px solid #131313`-toned border, logo + tagline column plus three link columns (12 links total), closing legal row with muted micro-copy.

## Graphics & Effects
The page-wide texture is a faint technical graph-paper grid: `linear-gradient(rgba(21, 21, 21, 0.05) 1px, rgba(0, 0, 0, 0) 1px)` layered with its 90°-rotated twin, each covering ~16.8% of visible pixels in a repeating tile — this is the dominant "graphic" of the whole page, not a decorative one-off, and should tile across every section at low opacity. A soft `radial-gradient(at 50% 0%, rgba(109, 92, 255, 0.5) 0%, rgba(0, 0, 0, 0) 60%)` violet wash appears only behind the final CTA panel, covering roughly 2.5% of the page — it is a corner accent on one boxed card, not a hero or page background; do not extend it upward into the hero, which stays flat paper-toned with the hero illustration's own black/red glyphs as its only color incident. Elevation throughout is conveyed by flat tonal shifts between `#E8E6DF` and `#DEDBD2`, never by drop shadow or blur — the system has no glassmorphism and no blur layer.

## Motion
Interactive color/border transitions run at `0.15s cubic-bezier(0.4, 0, 0.2, 1)` (button and link state changes), while transform-driven movement (hover lifts, press states) uses `0.3s cubic-bezier(0.22, 1, 0.36, 1)` — a snappy, slightly decelerating curve rather than a springy overshoot. A second transform group (`translate, scale, rotate`) runs at `0.3s cubic-bezier(0.4, 0, 0.2, 1)` for the hero diagram's floating diamond glyphs, and background/border fills ease at a plain `0.2s ease`. Named keyframes present in the system — `float`, `pulse-ring`, `shimmer`, `spin`, `bounce` — drive the orbiting diamonds, the dashed ring pulse, and loading/skeleton shimmer states around the AI hub graphic. Scroll physics run through Lenis for smoothed inertial scrolling; no scroll-linked reveals are dramatic — sections appear to settle in rather than fly in.

## Guardrails
- Never round a corner: every button, card, and badge is 0px radius — rounding any of them breaks the dossier identity.
- Keep the red accent to labels, numerals, and single glyphs; never fill a large surface or button with it.
- Do not extend the violet radial wash beyond the closing CTA panel — the hero and mid-page sections must stay flat paper-toned.
- Preserve the two-surface tonal system (`#E8E6DF` page / `#DEDBD2` card) — do not introduce drop shadows or blur for elevation.
- Body copy must stay in Spline Sans Mono; reserve Familjen Grotesk for headlines, labels, and numerals only.
- The navbar is edge-to-edge and square-cornered — do not inset it into a floating pill or rounded bar.