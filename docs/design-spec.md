# Ulap: Mood Diary · Design Spec (v1)

Prepared: November 4, 2026 · Status: Draft for review
Mockup canvas: https://claude.ai/artifact/Ecjc7A7vEuqp2xtL3mbaiy ("Mobile app · full flow" artboard)

> 📚 **Ulap docs:** [README](../README.md) · [Phases](../PHASES.md) · [Technical plan](technical-plan.md) · [API reference](api-spec.md) · **Design spec** · [Repo reference](ulap-reference.md)
>
> **This document owns:** colors, type, spacing, components, screen layouts, the Ulap mascot, motion, voice and tone, accessibility. Behavior rules (which response appears for which mood) come from [Technical plan §5](technical-plan.md#5-shared-domain-model). The exact app text lives in [Technical plan, Appendix B](technical-plan.md#appendix-b-response-message-keys-english-source).

---

## 1. Design principles

1. **Every feeling is welcome.** No mood is "bad." Red and Blue are information, not warnings. The UI never scolds, grades, or alarms.
2. **Validate first, then help.** Every response follows three steps: name the feeling → accept it → offer one small, optional action.
3. **Calm under stress.** The higher the stress, the simpler the screen: fewer choices, shorter text, slower motion.
4. **Honest over cheerful.** Ulap shares good moments and sits with hard ones. It never fakes positivity.
5. **Private and gentle.** No guilt, no streak resets, no "you missed a day."
6. **Readable at a glance.** Color always comes with a label or a shape. Never color alone.

---

## 2. Color

### 2.1 Neutrals

| Token | Hex | Use |
|---|---|---|
| `bg` | `#F4F5F8` | App background |
| `surface` | `#FFFFFF` | Cards, sheets, tab bar, inputs |
| `ink` | `#1B1F2A` | Primary text, primary buttons, selected states |
| `ink2` | `#2E3443` | Body text on tinted backgrounds |
| `ink3` | `#3B4252` | Secondary text, unselected nav labels |
| `muted` | `#5B6272` | Captions, helper text, axis labels |
| `placeholder` | `#6B7282` | Input placeholder |
| `line` | `#E1E4EA` | Card borders, dividers |
| `lineStrong` | `#C9CED8` | Input and chip borders |
| `lineSoft` | `#EEF0F4` | Chart gridlines, bar tracks, row dividers |
| `outlineButton` | `#A9B1BF` | Secondary button border |

### 2.2 Mood zones

Each zone has three roles: **strong** (dots, stripes, slider accent, selected tile), **text** (labels on white or tint), and **tint** (tile and response backgrounds).

| Zone | Meaning | Strong | Text | Tint |
|---|---|---|---|---|
| **Red** · Tier 1 | tense · high energy | `#C7392F` | `#A82E26` | `#FCECEA` |
| **Blue** · Tier 2 | heavy · low energy | `#2F62C9` | `#2754B0` | `#E8EFFC` |
| **Yellow** · Tier 3 | bright · high energy | `#E9AE1F` | `#7F5A00` | `#FFF5D9` |
| **Green** · Tier 4 | easy · low energy | `#267A4C` | `#22704A` | `#E3F4EA` |

Rules:
- Mood tiles use **tint** with `ink` text. Never put white text on Yellow strong. Use `ink` there.
- Response screens use the zone **tint** as the full-screen background, so the zone is felt without alarming the user.
- Red is never used for error states inside the check-in flow. Errors use `ink` text with an icon.

### 2.3 Stress bands (reports only)

| Band | Range | Bar | Chip background | Chip text |
|---|---|---|---|---|
| High | 7–10 | `#C7392F` | `#FCECEA` | `#A82E26` |
| Moderate | 4–6.9 | `#D9822B` | `#FDEFE2` | `#8A4A10` |
| Low | 0–3.9 | `#267A4C` | `#E3F4EA` | `#1B5E3B` |

Always show the band legend with the numeric range.

### 2.4 Semantic

| Token | Hex | Use |
|---|---|---|
| `positiveText` | `#1B5E3B` | "0.5 lower than last week," What-helped values |
| `positiveBg` | `#E3F4EA` | Saved confirmation |
| `info` | `#2754B0` | Links, hotline label |
| `infoBg` | `#E6EEFB` | Avatar, support notes |
| `infoBorder` | `#C9D6F2` | Hotline card border |
| `sampleBg` / `sampleText` | `#FFF5D9` / `#7F5A00` | "Sample data" badge |
| `heart` | `#E7848A` | Ulap gentle accent |
| `cheek` | `#F6B4AE` @ 70% | Ulap cheeks |
| `ulapStroke` | `#C3CAD6` | Ulap outline |

### 2.5 Contrast check

All text pairs meet **WCAG 4.5:1** for body text:
- `ink` on `bg`, `surface`, or any tint: ≥ 13:1
- `muted` (#5B6272) on `surface`: ≈ 6.2:1
- Zone `text` colors on `surface`: ≥ 5.5:1
- White on Red strong ≈ 4.9:1 · white on Blue strong ≈ 5.5:1 · white on Green strong ≈ 5.3:1

Run any new color pair through a contrast checker before use.

---

## 3. Typography

| Role | Font | Weight | Size (sp) | Line height | Use |
|---|---|---|---|---|---|
| `displayL` | Bricolage Grotesque | 700 | 26 | 1.1 | Selected mood name, screen titles (Logs, Reports, Account) |
| `displayM` | Bricolage Grotesque | 700 | 25 | 1.2 | Response title, after-check title |
| `displayS` | Bricolage Grotesque | 700 | 19–22 | 1.2 | Greeting bubble title, stat tile values |
| `bodyL` | Figtree | 400 | 16 | 1.45 | Response body, button labels |
| `body` | Figtree | 400 / 500 | 15 | 1.45 | Default text |
| `label` | Figtree | 600 | 14–15 | 1.3 | Mood tile name, section headings, slider labels |
| `small` | Figtree | 400 / 500 | 13–14 | 1.4 | Notes, helper text, chips |
| `caption` | Figtree | 400 / 600 | 11–12 | 1.3 | Tile meta ("Stress 9"), axis labels, overlines |
| `overline` | Figtree | 600, +4% tracking, UPPERCASE | 12 | 1.3 | Zone label above the mood name |

- Both fonts are free on Google Fonts (Open Font License). Bundle them in `res/font/` or use downloadable fonts.
- Fallback: system sans-serif.
- Numbers in stats, sliders and charts use **tabular figures** (`fontFeatureSettings = "tnum"`).
- Support **font scaling up to 200%**: text wraps, never truncates mood names, and tiles grow in height.

---

## 4. Spacing, shape and elevation

### Spacing scale (dp)
`4 · 8 · 12 · 16 · 20 · 24 · 32 · 48`

- Screen side padding: **16** (20 on the response screens)
- Gap between cards: **16**
- Card inner padding: **12–16**
- Gap inside a mood grid: **8**

### Corner radius (dp)

| Token | Value | Use |
|---|---|---|
| `radiusS` | 10 | Icon buttons, small inner cards |
| `radiusM` | 12 | Mood tiles, inputs |
| `radiusL` | 14 | Buttons, stat tiles, log rows |
| `radiusXL` | 16–18 | Cards, mood header card, support cards |
| `radiusBubble` | 18 / 18 / 18 / 4 | Ulap speech bubble (bottom-left corner points at Ulap) |
| `radiusPill` | 999 | Chips, badges, switch, bar tracks |

### Elevation
Flat. Separate surfaces with a **1dp `line` border**, not shadows. The only "shadow" is Ulap's soft ellipse (8% ink).

---

## 5. Iconography

- Line icons, **1.8dp stroke**, round caps and joins, 22dp in the tab bar, 16–18dp inline.
- Tab icons: plus-in-circle (Check in), three lines with dots (Logs), four vertical bars (Reports), person (Account).
- No emoji in the UI.
- Icon-only buttons always have a content description (e.g. "Back to moods," "Edit or delete this check-in").

---

## 6. Components

### 6.1 Mood tile
- Size: fills 1/3 of the row (3-column grid), min height **52dp**, radius 12, padding 8×10.
- Background: zone **tint**, no border.
- Content: mood name (`label` 14, ink) + "Stress N" (`caption` 11, `#4A5162`).
- Pressed: 96% scale for 100ms. Tapping opens Details (no selected state on the grid).
- Accessibility: "Worried. Red zone. Default stress 8."

### 6.2 Zone section card
- `surface`, 1dp `line` border, radius 16, padding 12, gap 10.
- Header: 10dp zone dot (radius 3) + zone name (`label` 15) + descriptor (`caption` 12, muted), e.g. "Red Zone · tense · high energy".
- Order is always Red → Blue → Yellow → Green.

### 6.3 Ulap speech bubble
- `surface`, 1dp `line` border, radius 18/18/18/4, padding 10×14.
- Sits to the right of Ulap, bottom-aligned with Ulap's base.
- Line 1: context (`small`, muted). Line 2: message (`displayS` or `body` 600).

### 6.4 Slider (Energy, Stress)
- Material 3 Slider, steps 1–10 (9 steps), height 32dp touch area.
- Active track and thumb: selected mood's zone **strong** color.
- Header row: label (600) on the left, value "**8** / 10 · high" on the right.
- End labels under the track: Energy "Drained … Energized", Stress "Very low … Very high".
- Words for stress: 1–2 very low · 3–4 low · 5–6 moderate · 7–8 high · 9–10 very high.
- Words for energy: 1–3 low · 4–6 medium · 7–10 high.

### 6.5 Trigger chip
- Pill, min height 40dp, padding 6×14, 1.5dp border.
- Unselected: `surface` bg, `lineStrong` border, ink text.
- Selected: `ink` bg and border, white text. Tap again to clear.
- Single-select. Options: Work, Family, Money, Health, Relationships, School, Other.

### 6.6 Buttons

| Type | Height | Style |
|---|---|---|
| Primary | 52 | `ink` fill, white text 16/600, radius 14 |
| Secondary | 48 | Transparent, 1dp `outlineButton` border, ink text 15/500 |
| Tertiary (outline strong) | 48 | `surface` fill, 1.5dp `ink` border. Used for "Check in." |
| Text link | ≥44 touch | `info` color, underline. Used for "Reset to default." |
| Icon button | 44×44 | `surface` fill, radius 12 |

At most **one primary button per screen**. When stress ≥ 8, show only the primary.

### 6.7 Cards
- Standard: `surface`, 1dp `line`, radius 16, padding 14.
- On tinted backgrounds (response screen): `surface`, no border, radius 18, padding 16–20.

### 6.8 Hotline card
- `surface`, 1dp `infoBorder`, radius 18, padding 18.
- Label "NCMH Crisis Hotline · available 24/7" (13/600, info) → number **1553** (Bricolage 34/700) → primary button "Call 1553" (opens the dialer) → helper line.
- Never animated. Never decorated.

### 6.9 Breathing circle
- 140dp circle, Red tint fill, 3dp Red strong border.
- Animation: grow for 4s (inhale) → hold for 2s → shrink for 6s (exhale). Loops, 12s per cycle.
- Text below: "In for 4 · hold for 2 · out for 6" / "Follow the circle. Five slow breaths is enough."
- With reduced motion: a static circle plus a text counter.

### 6.10 Bottom tab bar
- `surface`, 1dp top `line` border, 4 equal columns, item min height 52dp, bottom padding 14 plus the system inset.
- Active: ink icon and label 12/600. Inactive: `muted` 12/500.
- Tabs: **Check in · Logs · Reports · Account**.

### 6.11 Segmented control (Daily / Weekly / Monthly)
- Container: `surface`, 1dp `line`, radius 12, padding 4, 3 equal segments.
- Segment: height 40, radius 9. Selected: `ink` fill, white text. Unselected: transparent, `ink3` text.

### 6.12 Filter chips (Logs)
- Horizontally scrolling row: All · Red · Blue · Yellow · Green.
- Each chip shows an 8dp zone dot before its label. Selected style is the same as a trigger chip.

### 6.13 Log row
- `surface`, 1dp `line`, radius 14, padding 12.
- A 4dp vertical **zone stripe** on the left (radius 4).
- Line 1: mood (600) + "9:15 AM · Stress 8 · Work" (caption, muted).
- Line 2: note (`small`, ink3). 2 lines max, then "…".
- Right: a 36dp overflow button (Edit, Delete).
- Day header above each group: "Today" (600) on the left, "5 check-ins · avg stress 4.4" on the right.

### 6.14 Stat tile
- 2-column grid, `surface`, 1dp `line`, radius 14, padding 12.
- Label (caption 12, muted) → value (Bricolage 22/700, tabular) → sub (12, muted or `positiveText` when the change is good).

### 6.15 Charts
- **Vertical bar chart (stress trend):** height 130dp, gridlines every 2 points (20%) in `lineSoft`, baseline `lineStrong`, bars colored by stress band, top radius 4, max width 30dp. Labels under the bars (11sp). Monthly shows labels only on days 1, 8, 15, 22 and 29.
- **Horizontal bar (mood frequency, triggers, what helped):** 10dp track in `lineSoft`, pill radius. Fill: zone color (moods), `ink3` (triggers), Green strong (what helped). Value on the right, tabular.
- **Stress chip:** pill 46dp wide, band colors from §2.3.
- Every chart has a text title and a one-line subtitle explaining the scale.
- **Library (D9):** the stress trend chart uses **Vico** (Compose). Bars get their band color one by one, and the chart's TalkBack text is the summary sentence in §11, set on the chart's semantics. The horizontal bars and the stress chip are plain Compose rows, so they need no chart library. If Vico can't color single bars or carry the summary, draw the trend with Compose `Canvas` instead and record that in [ulap-reference §13](ulap-reference.md#13-decision-log).

### 6.16 Switch
- Material 3 Switch. On: Green strong track. Off: `outlineButton` track.
- Used for "Show Ulap the cloud."

### 6.17 Badges
- Pill, 12/600, padding 3×10. Examples: "Sample data," zone level ("Highest stress").

---

## 7. Screens

All screens: 360–412dp wide phones, portrait. Content scrolls above a fixed tab bar.

### 7.1 Onboarding (3 steps)
1. **Choose your language:** three large options (English · Filipino · Bisaya), each with a short greeting in that language.
2. **Meet Ulap:** Ulap (happy) + "Hi, I'm Ulap. I'll keep you company while you check in with your feelings. Any feeling is okay here."
3. **Your privacy:** "Your diary stays on this phone. You can back it up later." + "Ulap is a self-care diary, not a medical or emergency service." + primary **"Start my first check-in"** + text link "Read the privacy notice."

No account screen in onboarding.

### 7.2 Check in · Pick a mood
- Top: Ulap (neutral, 88dp, floating) + speech bubble: "Good afternoon, {name}" / **"How are you feeling right now?"** / "Any answer is okay."
- Four zone section cards with 3-column mood tiles.
- Guest users: greeting without a name ("Good afternoon").

### 7.3 Check in · Details
- Top: back button + date and time ("Wednesday, Nov 4 · 4:49 PM").
- Mood header card (zone tint, radius 18): Ulap (zone expression, 82dp) + overline zone name + mood name (displayL) + Ulap's soft-mirror line.
- Energy slider → Stress slider (defaults from the mood). If the user changed a value: "Default for Worried: energy 6, stress 8 · **Reset to default**".
- "What triggered it?" chips.
- "Journal · optional" text area (4 lines, grows). The placeholder is the zone prompt.
- Primary **Save**.

### 7.4 Check in · Response (sympathetic)
Which actions appear is decided by `ResponseRules` ([Technical plan §5](technical-plan.md#5-shared-domain-model)).

- Full-screen zone tint background.
- Ulap 168dp, centered, floating (still for Hopeless).
- Title (displayM, centered) → body (bodyL) → trigger line (body, ink3) when relevant.
- Actions: primary (zone action) + secondary (only when stress < 8).
- After the primary tap: a support card replaces the buttons (breathing circle / small helps list / "Saved to your good moments").
- Red and Blue zones: a check-in card at the bottom: "When you're ready" / "How stressed do you feel now?" + slider (starts at the logged stress) + **Check in**.
- Hopeless: hotline card instead of action buttons. The check-in card is still shown.

### 7.5 Check in · After
- Ulap 150dp (calm when stress dropped, gentle otherwise).
- "Before 8 → Now 5" pills.
- Title + sub (see §10.4).
- Primary **Done** · secondary **See my week**.

### 7.6 Logs
- Title "Logs" → search field (46dp, radius 14) → zone filter chips → day groups of log rows.
- Empty search result: "No check-ins match. Try another word or zone."
- Empty diary: Ulap (neutral) + "Your check-ins will show up here." + primary "Check in now."
- Edit opens Details filled in. Delete asks first: "Delete this check-in? This can't be undone." (Delete · Keep).

### 7.7 Reports
- Title + "Sample data" badge (mockup only) → Daily/Weekly/Monthly control → period label ("Oct 29 – Nov 4").
- Ulap (calm, 64dp) + bubble "**Ulap noticed:** …".
- 2×2 stat tiles → stress trend chart → What helped most → Top triggers.
- Not enough data (fewer than 3 check-ins in the period): Ulap + "A few more check-ins and I'll start seeing patterns with you."

### 7.8 Account
- Profile card (avatar initial, name, "Checking in since October 2026"). For a guest: "**Back up and sync**: Keep your diary safe across phones" + Continue with Google · Use email instead.
- Signed in: a sync row with **Sync now** and the last-synced time. When check-ins stopped trying to upload ([Technical plan §8](technical-plan.md#when-a-change-cant-upload)), a quiet notice below it: "%d check-in couldn't back up yet. It's still saved on this phone." / "%d check-ins couldn't back up yet. They're still saved on this phone." + **Try again** (text button). Android `plurals`. Never show how many times it was tried. Say "saved", not "safe": a check-in only on this phone is lost with the phone.
- Settings list: Show Ulap the cloud (switch) · Language · Gentle reminder (later) · Quiet hours (later) · Export my data (later) · Need to talk to someone? NCMH 1553 · Sign out · **Delete account and all data**.
- Footer: "Reminders never count missed days. Your check-ins are private to you."

### 7.9 Sign in (sheet)
- Bottom sheet: Ulap (gentle) + "Back up your diary" + "Continue with Google" (Google's standard button) + "Use email instead" (secondary).
- Email form: email, password (show/hide), primary "Create account" / "Sign in", link "Forgot password?".
- Error text sits under the field in ink with an icon. It doesn't use Red.

---

## 8. Ulap the mascot

### 8.1 Character
A small, soft cloud. Calm, kind, a little playful. Ulap is a **companion, not a coach**: it keeps the user company and never pushes.

### 8.2 Construction (vector, viewBox 130 × 110)
- **Body path:** `M30 90 A22 22 0 0 1 24 47 A30 30 0 0 1 80 30 A26 26 0 0 1 112 60 A20 20 0 0 1 104 90 Z`
- Fill `#FFFFFF`, stroke `#C3CAD6` 2.5, round joins.
- **Shadow:** ellipse cx 66, cy 100, rx 38, ry 5, ink at 8%.
- **Cheeks:** circles (47, 71) and (85, 71), r 5, `#F6B4AE` at 70%.
- **Face strokes:** ink, 3 width, round caps.

### 8.3 Expressions

| Expression | Used for | Eyes | Brows | Mouth | Accent |
|---|---|---|---|---|---|
| `neutral` | Pick-mood greeting, empty states | Dots r 3.5 at (55,62), (77,62) | none | Small smile `M60 72 Q66 77 72 72` | none |
| `concerned` | Red zone | Dots r 3.5 at y 63 | Inner ends raised: `M47 55 L58 52`, `M74 52 L85 55` | Soft line `M60 75 Q66 73 72 75` | none |
| `gentle` | Blue zone, after-check (no change / higher) | Soft closed arcs `M50 62 Q55 66 60 62`, `M72 62 Q77 66 82 62` | none | Small smile `M61 73 Q66 76 71 73` | Heart top-right `#E7848A` |
| `happy` | Yellow zone | Smiling arcs `M50 64 Q55 58 60 64`, `M72 64 Q77 58 82 64` | none | Open smile `M58 70 Q66 82 74 70 Z` (filled) | Two sparkles `#E9AE1F` |
| `calm` | Green zone, reports, after-check (stress lower) | Closed arcs (as gentle) | none | Smile `M60 72 Q66 77 72 72` | Leaf top-right `#3E9A6A` / vein `#267A4C` |
| `quiet` | Hopeless | Dots r 3 | none | Short flat line `M62 74 L70 74` | none, **no float** |

### 8.4 Sizes

| Place | Size (width) |
|---|---|
| Report bubble | 64dp |
| Details header | 82dp |
| Pick-mood greeting | 88dp |
| After check-in | 150dp |
| Response | 168dp |

### 8.5 Behavior rules
- ✅ Shows a **softened** version of the user's zone, then offers comfort.
- ✅ Celebrates good moments with the user.
- ❌ Never cries, panics, rages, or looks sick.
- ❌ Never reacts to missed days or to how often the user logs.
- ❌ Never appears decorative on the hotline card.
- The user can turn Ulap off. All messages still appear, without the character.
- Content description: "Ulap the cloud, {expression description}" (e.g. "Ulap looking concerned but calm").

### 8.6 Later: Ulap illustration set
For the store listing and onboarding, commission or draw a polished version that keeps the same silhouette, colors and the six expressions. Keep it **original**: no resemblance to existing cloud mascots.

---

## 9. Motion

| Motion | Spec | Reduced motion |
|---|---|---|
| Ulap float | translateY 0 → −5dp → 0, 3.6s, ease-in-out, infinite | Off |
| Breathing circle | Scale 0.6 → 1.0 (4s) → hold (2s) → 0.6 (6s) | Static circle plus a text counter |
| Screen transition | Fade plus 8dp slide up, 220ms, standard easing | Fade only |
| Tile press | Scale 0.96, 100ms | Off |
| Response enter | Ulap fades in 200ms, then the text 150ms later | Instant |

- Red and Blue responses use **slower, softer** transitions (e.g. 280ms). Yellow can add a short sparkle pop (300ms) once.
- Follow the system "Remove animations" setting.

---

## 10. Voice and tone

### 10.1 Voice
Warm, plain, short. Ulap talks like a kind friend, not a therapist or a cheerleader.

### 10.2 Rules
- **Name, accept, offer.** "That's a lot to carry right now." → "That's okay." → "Want to breathe with me?"
- Second person, present tense, short sentences.
- Offer, never order: "Want to…?", "Only if it feels right."
- Patterns, not grades: "Mondays tend to be harder for you," not "You failed on Mondays."
- No exclamation marks in Red and Blue responses.
- Reuse the user's own mood word in the message ("Feeling worried makes sense…").

### 10.3 Words

| Use | Avoid |
|---|---|
| check-in, feeling, mood | score, rating, performance |
| harder, heavy, a lot | bad, negative, wrong |
| "Any answer is okay." | "Try to be positive!" |
| "Thanks for being honest." | "Cheer up!" |
| "When you're ready" | "You must," "You should" |
| "You checked in 18 days this month." | "You missed 13 days." |
| "I'm here when you need me." | "Don't break your streak!" |

### 10.4 After check-in messages

| Result | Title | Sub |
|---|---|---|
| Stress lower | Your stress went from 8 to 5. | That's 3 points lower. Remember what helped, so you can use it again. |
| Same | Still at 8, and that's okay. | Feelings take time to shift. Be gentle with yourself today. |
| Higher | It went up to 9. | Thank you for checking honestly. If it keeps rising, reach out to someone you trust or call NCMH at 1553. |

These match the `after_*` keys in [Technical plan, Appendix B](technical-plan.md#appendix-b-response-message-keys-english-source), which is the source for all response strings.

### 10.5 Languages
- English, Filipino and Bisaya are written to the **same tone**. Translate meaning, not word for word.
- Everyday Taglish and Bislish are fine where they sound more natural.
- Every Filipino and Bisaya string gets a native-speaker review before release.
- Layouts must fit text up to **40% longer** than English: buttons wrap to 2 lines, and tiles grow.

---

## 11. Accessibility

- Touch targets **≥ 48dp** on Android (mockup tiles at 52, chips at 40 high get extra padding).
- Text contrast ≥ 4.5:1 (§2.5).
- Zone is never shown by color alone: there's always the zone name, a dot plus a label, or a stripe plus the mood name.
- TalkBack:
  - Mood tile: "{Mood}. {Zone}. Default stress {n}."
  - Slider: "Stress, 8 of 10, high."
  - Chart: a summary sentence ("Daily average stress this week. Highest Monday, 6.8. Lowest Sunday, 2.4.").
  - Ulap: a content description per expression. Mark it decorative when it's next to a bubble that already says the same thing.
- Font scaling up to 200% without clipping.
- Reduced motion respected (§9).
- The hotline is reachable in **2 taps** from anywhere (Account → hotline row).

---

## 12. Jetpack Compose mapping

```kotlin
// theme/Color.kt
object UlapColors {
    val Bg = Color(0xFFF4F5F8)
    val Surface = Color(0xFFFFFFFF)
    val Ink = Color(0xFF1B1F2A)
    val Ink2 = Color(0xFF2E3443)
    val Ink3 = Color(0xFF3B4252)
    val Muted = Color(0xFF5B6272)
    val Line = Color(0xFFE1E4EA)
    val LineStrong = Color(0xFFC9CED8)
    val LineSoft = Color(0xFFEEF0F4)
    val OutlineButton = Color(0xFFA9B1BF)
    val PositiveText = Color(0xFF1B5E3B)
    val Info = Color(0xFF2754B0)
    val InfoBg = Color(0xFFE6EEFB)
}

data class ZoneColors(val strong: Color, val text: Color, val tint: Color)

val ZonePalette = mapOf(
    Zone.RED to ZoneColors(Color(0xFFC7392F), Color(0xFFA82E26), Color(0xFFFCECEA)),
    Zone.BLUE to ZoneColors(Color(0xFF2F62C9), Color(0xFF2754B0), Color(0xFFE8EFFC)),
    Zone.YELLOW to ZoneColors(Color(0xFFE9AE1F), Color(0xFF7F5A00), Color(0xFFFFF5D9)),
    Zone.GREEN to ZoneColors(Color(0xFF267A4C), Color(0xFF22704A), Color(0xFFE3F4EA)),
)
```

```kotlin
// theme/Type.kt
val Bricolage = FontFamily(Font(R.font.bricolage_grotesque_semibold, FontWeight.SemiBold),
                           Font(R.font.bricolage_grotesque_bold, FontWeight.Bold))
val Figtree = FontFamily(Font(R.font.figtree_regular), Font(R.font.figtree_medium, FontWeight.Medium),
                         Font(R.font.figtree_semibold, FontWeight.SemiBold))

val UlapTypography = Typography(
    headlineMedium = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 29.sp),
    headlineSmall  = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.Bold, fontSize = 25.sp, lineHeight = 30.sp),
    titleMedium    = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 23.sp),
    bodyLarge      = TextStyle(fontFamily = Figtree, fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium     = TextStyle(fontFamily = Figtree, fontSize = 15.sp, lineHeight = 22.sp),
    labelLarge     = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    bodySmall      = TextStyle(fontFamily = Figtree, fontSize = 13.sp, lineHeight = 18.sp),
    labelSmall     = TextStyle(fontFamily = Figtree, fontSize = 11.sp, lineHeight = 14.sp),
)
```

```kotlin
// theme/Shape.kt
val UlapShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(18.dp),
)
val BubbleShape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 4.dp)
val ButtonShape = RoundedCornerShape(14.dp)
```

- Zone, Mood and other domain types come from the shared module ([Technical plan §5](technical-plan.md#5-shared-domain-model)).
- Map Material roles: `primary = Ink`, `onPrimary = White`, `background = Bg`, `surface = Surface`, `outline = LineStrong`, `outlineVariant = Line`.
- The sketch above is simplified. The code in `core/designsystem/.../theme/` differs in three ways. `UlapColors` is a class holding every §2 token (including stress bands and the mascot), read through `UlapTheme.colors`, so dark mode can be a second instance. `overline` maps to `labelMedium`. The other Material roles are filled from the neutrals, so no Material purple shows through. Three colors used in §6.1 and §8 are tokens too: `mutedOnTint` (#4A5162), `ulapBody` (#FFFFFF) and `leaf` (#3E9A6A).
- The fonts are Google Fonts' variable files (`res/font/`), with weights picked in `Type.kt`. Figtree also loads 700 for bold numbers inside text.
- §8.3 names the heart, sparkle and leaf accents but gives no paths. The paths `Ulap.kt` draws are the reference until the illustration set (§8.6) replaces them.
- Build Ulap as one `@Composable fun Ulap(expression: UlapExpression, size: Dp, float: Boolean)` drawing with `Canvas` and the paths in §8.2–8.3 (or as six vector drawables).

---

## 13. Later: dark mode

The tokens are named by role (`bg`, `surface`, `ink`, zone `strong/text/tint`), so dark mode is a second palette, not a redesign. Guidelines:
- `bg` near `#12151C`, `surface` `#1B1F2A`, `ink` `#EEF0F4`.
- Zone tints become deep, low-saturation versions (e.g. Red tint `#3A1F1D`). Zone strong colors stay bright enough for 3:1 against the surface.
- Ulap keeps its white body, with a slightly lighter stroke.
- Re-check every pair against WCAG.

---

## 14. Open design items

Tracked in [PHASES.md](../PHASES.md) (Phases 5, 8 and 10).

- [ ] Final Bisaya and Filipino wording ([Technical plan, Appendix A](technical-plan.md#appendix-a-mood-names-first-draft-needs-review) and [B](technical-plan.md#appendix-b-response-message-keys-english-source))
- [ ] Polished Ulap illustration set for the store listing and onboarding
- [ ] App icon (Ulap on a zone-color background, tested at 48dp)
- [ ] Play Store screenshots (5): Pick mood · Response (Red) · After check-in · Reports · Logs
- [ ] Empty, loading and error states for sync ("Saved on this phone. Will back up when you're online.")
