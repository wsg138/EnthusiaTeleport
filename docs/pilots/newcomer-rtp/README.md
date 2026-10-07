# Newcomer activation preparation — October 7, 2026

Status: review/staging configuration only. Production remains read-only. No upload,
save, reload, restart, permission edit, player message or merge is authorized by
this preparation. Source defaults remain disabled.

## Scope and provenance

Authoritative repository: wsg138/EnthusiaTeleport. Current main was fetched at
`be5cef1cb1f47c1b1ff6035b72a832f26127ccb3`, the merge of
[PR #18](https://github.com/wsg138/EnthusiaTeleport/pull/18). PR #16 was superseded,
not merged. The isolated preparation branch starts from this current main and
preserves previous checkouts.

Read-only production inspection on October 7 found EnthusiaTeleport 1.2.10
enabled in the latest SMP startup log at 00:19:20 server log time. This verifies
the loaded version, not an artifact hash or merged-source build provenance.
Config inspected around 11:52 UTC had newcomer and onboarding disabled. RTP
region exclusions and spacing were already configured. The selected settings
snapshot is evidence, not an installable config; refresh live settings before
any later application.

## Concrete proposed change

Merge `activation-overlay.yml` into the existing SMP Teleport configuration.
Only two observed values change: `rtp.newcomer.enabled` and `onboarding.enabled`,
both false -> true. The overlay explicitly retains three total uses, 86,400
elapsed seconds and the enabled boss-bar option. Do not replace the full config
with this overlay or with repository defaults: production has different default
RTP/home limits, coordinates, cooldowns, rank mappings and spacing.

The user journey is:

1. First SMP join: existing spawn and starter kit, plus readable clickable chat:
   **[Start Survival] Use /rtp to find wilderness and build a home.**
2. During the first 24 elapsed hours from first play on SMP, eligible players see
   a separate top-of-screen bar showing `/rtp`, their effective remaining uses
   and time until the newcomer window ends. Default players get three total
   successful uses, including uses already recorded. Higher/unlimited rank limits
   remain authoritative. The bar does not extend aNewbie protection.
3. After the player's first ever successful RTP, while within the first day and
   with no named home, a positive home limit and sethome permission, chat says:
   **Found a place you like? Use /sethome base to save it, then /home base to return.
   You can explore first.** Clicking suggests the command; the player submits it.
4. The player returns voluntarily to their saved project. `/daily`, existing
   challenges and rewards remain available through their existing behavior;
   this increment adds no daily reminder or reward-guide command.

Existing message keys are `onboarding.start-survival`, `onboarding.first-home`
and `rtp.newcomer-boss-bar`. No message override is required: merged source has
fallbacks if the keys are absent. Inspect customized live messages before later
activation; blank templates suppress their corresponding notices.

The join prompt is first-backend-join only. Enabling does not resend it to old
accounts. Accounts still in their first 24 hours can receive the allowance/bar;
an account that has already used RTP does not receive the first-success home
prompt retrospectively. Missing/future first-play timestamps grant no bonus.
There is no quota reset, daily replenishment, home creation or protection change.

## Runtime acceptance sheet (all pending)

Run these checks on an isolated Paper runtime matching SMP's companion versions,
with an independently verified merged-source artifact. Existing SMP Test data
may already mark owner accounts as returning; use fresh ordinary Java and Bedrock
identities. Do not delete/reset production records to simulate a newcomer.

| Check | Expected result | Evidence/status |
|---|---|---|
| Fresh Java and Bedrock entry | Effective `enthusia.teleport.rtp`, `enthusia.teleport.sethome` and `enthusia.teleport.home` access; first-play timestamp available; readable start prompt | Pending |
| Boss-bar coexistence | Correct remaining uses/time alongside aNewbie; readable at ordinary client UI scale; visible within refresh interval | Pending |
| Three successes / fourth denial | Counts 0 -> 1 -> 2 -> 3; fourth denied; finite higher rank retained; unlimited rank remains unlimited | Pending |
| Failed/cancelled RTP | Movement/combat cancellation, timeout and blocked teleport consume no successful use and show no first-home prompt | Pending |
| Protected destinations | Inside/on boundaries of warzone/spawn/market rejected, including safe-search shifts and region changes during warmup; origin at spawn remains usable | Pending |
| Missing region/provider | Fail closed; report problem without consuming a use | Pending |
| First home | Only first successful RTP prompts; no existing home overwrite; `/sethome base` then `/home base` work; Bedrock text works without clickable support | Pending |
| Rejoin/restart | Persistent use count retained; no repeated first-success home prompt; owned boss bars cleaned up | Pending |
| Expiry and policy rollback | At 24 elapsed hours bonus/bar end; previously consumed uses retained; queued request revalidates; no protection extension | Pending |
| Turning onboarding off | No new onboarding prompts after approved activation of rollback settings | Pending |

Record runtime/artifact version, SHA-256, source commit, companion versions,
client edition and timestamps with each result. A passed local unit suite does
not close these player/client gates. No in-game acceptance is claimed here.

## Later rollout and rollback

1. Complete source review of this preparation; verify exact-head checks. The
   current merged feature does not require a new plugin implementation.
2. Resolve release ownership and the monorepo `plugins/enthusia-teleport` pin;
   verify clean merged-source build/version/hash and combined checks. A loaded
   1.2.10 version alone does not satisfy provenance requirements.
3. Stage on the isolated runtime with explicit operational authorization; finish
   the acceptance sheet. This document itself grants no upload/restart rights.
4. Obtain specific production configuration and activation authorization only
   after the proposed settings and acceptance evidence are reviewable. Re-read
   current config/messages/regions/permissions and back up affected files.
5. Apply only the approved key changes and activate using the approved lifecycle.
   If plugin reload is chosen, disclose that its current command clears pending
   teleports, requests and cooldowns. No reload is performed by this work.
6. Roll back by merging `rollback-overlay.yml` and using the separately approved
   lifecycle. Preserve `rtp_uses.yml`, homes, region exclusions, spacing and rank
   settings. Counts above the normal limit remain exhausted; never delete them.

The Hub Start Survival item and Tags daily/guide/browser PRs require their own
source reconciliation, review, clean release and client acceptance. They are not
dependencies for this two-flag RTP pilot and are not silently included in it.

## Measurement and SPEAR evidence

RET-007 covers activation preparation: WHEN a newcomer activation plan is
prepared THE SYSTEM SHALL retain opt-in source defaults and provide bounded
activation/rollback overlays plus pending runtime acceptance without changing
production.

SPEC: RET-007 and this exact two-value difference. PROVE: October 7 read-only
config demonstrates the two disabled flags; startup log demonstrates version
1.2.10 loading. Existing feature tests and owner validation are documented in
`../../verification.md`; no historical red test is invented. ENGINE: no new
behavioral implementation is needed for documentation/configuration preparation.
ARCH: existing permissions, successful-use persistence, live WorldGuard checks,
CombatLogX and aNewbie boundaries are retained. REFINE: YAML parsing, bounded
overlay/rollback comparison and whitespace checks are recorded in that evidence
file. No project-local EARS validator or SPEAR state helper exists.

Refresh collection against real joins before treating Plan sessions as a complete
baseline. Use the existing pilot's seven complete baseline days / fourteen pilot
days, report cohort counts and mature D1 (hours 24–48) / D7 (hours 168–192) windows,
first-session under-two-minute exits, non-AFK play and Hub -> SMP transitions.
Do not invent RTP/home events from aggregate analytics. Track Java/Bedrock and
acquisition mix where actually available. No retention improvement is claimed.
