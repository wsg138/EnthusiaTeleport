# Newcomer RTP pilot requirements

## RET-007 - Bounded activation preparation
WHEN a newcomer activation plan is prepared THE SYSTEM SHALL retain opt-in source defaults and provide bounded activation/rollback overlays plus pending runtime acceptance without changing production.

Acceptance: only the two observed enable flags change; three total uses, 24 elapsed hours, existing counts/permissions/ranks/regions and spacing remain intact. Overlays are explicitly incomplete and must be merged, never used as replacement configs. Source/loaded-version/config evidence and unresolved artifact/client gates remain distinct. See docs/pilots/newcomer-rtp/README.md.

## RET-006 - Survival entry and first-home guidance
WHEN an opted-in onboarding installation receives a first backend join with enabled RTP and permission THE SYSTEM SHALL show a readable Start Survival action executing the existing `/rtp` command.

WHEN a valid newcomer completes their first successful RTP and has no home, a positive home limit and sethome permission THE SYSTEM SHALL suggest `/sethome base` and explain `/home base` without executing either command.

Acceptance: disabled by default; existing RTP counter identifies first success across relogs/reloads; failures never trigger the prompt; future/unknown/expired first-play timestamps suppress the home prompt. Readable commands work without click support; Java supports a click-to-suggest home command. No auto-created home, no new persistence or bypass of command permissions/combat/region checks.

## RET-005 - Protected-area destination exclusion
WHEN RTP searches for or completes a destination THE SYSTEM SHALL reject any point in the configured WorldGuard regions warzone, spawn or market, regardless of rank, region membership, priority or teleport bypass permissions.

Acceptance: inspect live WorldGuard region geometry, including overlapping regions and safe-search adjustments; recheck at warmup completion; failed/blocked attempts consume no use. Missing provider, missing configured region or unavailable region manager fails closed for RTP. Destination exclusion is the stated assumption after optional clarification; spawn remains an allowed origin so newcomers can start survival. Other teleports retain their semantics. No production changes.

## RET-004 - On-screen discovery
WHILE an online player has RTP permission, an enabled newcomer allowance, a valid unexpired first-play window and remaining RTP uses THE SYSTEM SHALL display a separate boss bar with the effective remaining uses, `/rtp` and time until the newcomer window ends, unless the boss-bar option is disabled.

Acceptance: higher rank limits are reflected; unlimited limits say unlimited. Refresh once per second; remove on exhaustion, expiry, permission loss, disconnect, config reload and plugin disable. Do not write to the action bar used by aNewbie. No aNewbie API dependency. Java/Bedrock layout remains a client acceptance gate.

## RET-001 - Optional newcomer allowance
WHEN the newcomer RTP pilot is enabled and a player's valid first-play timestamp is within the configured elapsed-time window THE SYSTEM SHALL raise their finite RTP limit to at least the configured newcomer total.

Acceptance: suggested pilot is three total successful uses within 24 hours of first play on this backend. This is a floor, not three additional uses. Missing, future, expired timestamps and disabled/invalid settings grant no bonus. Existing default and rank permissions remain authoritative; the pilot never reduces a limit or converts unlimited access into finite access.

## RET-002 - Persistent usage and boundaries
WHEN a newcomer uses RTP THE SYSTEM SHALL retain the existing UUID usage counter, successful-teleport accounting, permission requirement, combat rules, safety checks and queue budgets.

Acceptance: relogging, reloading and enabling/disabling the pilot do not reset uses. Failed searches and cancelled/failed teleports retain the existing behavior of not consuming a use. The new policy has no platform, scheduling, persistence or protection-plugin dependency.

## RET-003 - Safe configuration compatibility
WHEN an existing installation loads without newcomer settings THE SYSTEM SHALL keep the pilot disabled and preserve the existing RTP settings constructor.

Acceptance: no default gameplay changes; no automatic activation or production edits. Existing uses above the temporarily available limit are preserved when the window expires. The window uses elapsed real time, including offline time, independently of aNewbie's protection timer.
