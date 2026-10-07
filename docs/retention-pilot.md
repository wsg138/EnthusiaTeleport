# First retention pilot: starting survival

October 7 status: feature source is merged through canonical PR18 at be5cef1;
production startup loads 1.2.10 but the newcomer and onboarding enable flags
remain false. Current activation/rollback preparation and pending client gates
are in [the activation plan](pilots/newcomer-rtp/README.md). Earlier observations
and incremental feature descriptions below are historical; current production
has 1000-block spawn and 750-block recent-RTP spacing. PR18 also corrects unlimited
rank resolution. Do not use the old zero-spacing or unchanged-rank statements
below as current production/source evidence.

## Evidence and objective

Read-only Plan inspection on October 5, 2026 found 239 network newcomers in 30 days and 70 in seven days. Hub had 3,099 sessions in the September 28-October 5 comparison, while SMP initially had 36. A subsequent SMP read showed 37, total sessions increasing from 62,643 to 62,644, and increasing playtime and mob kills. Collection is active now; recent historical SMP coverage remains incomplete. The SMP calendar displayed 37 sessions on October 5 and none on October 1-4. This is a collection baseline, not a measured abandonment rate.

The network retention help states that it compares first registration to last seen. It is not exact-day return retention. Recent immature cohorts, the SMP's 0/3 figure, Hub transfers and the collection gap must not be interpreted as proof of survival abandonment. The SMP all-time peak is 96 on May 9, 2026; the newly recorded network peak is not comparable historical capacity evidence.

Production Teleport configuration inspected earlier in this conversation gives first joins stone tools and eight beef, one default RTP use within +/-5,000, and zero minimum RTP spacing. aNewbie config provides 30 minutes of player-damage protection. These are configuration observations; effective permissions and Java/Bedrock client behavior still require acceptance checks.

Hypothesis: an unsatisfactory first wilderness destination with no retry may discourage newcomers. Three total successful RTP uses during the first 24 elapsed hours may improve entry into survival. The additional allowance is optional and disabled by default. A separate top-of-screen boss bar now explains `/rtp`, effective remaining uses and the newcomer window. It leaves aNewbie's action bar and protection state untouched.

Read-only production aNewbie config on October 5 confirmed both action bar and green boss bar enabled. The RTP boss bar appears within the next one-second refresh on join/rejoin for eligible players, stays while uses remain during the first-play window, updates within one second of use and disappears on expiry/exhaustion/permission loss/disconnect. It clears on reload/disable. Higher ranks use their actual computed limits; the time label explicitly refers to the newcomer window rather than rank expiry. `rtp.newcomer.boss-bar-enabled: false` opts out; `rtp.newcomer-boss-bar` in messages.yml customizes the text. No chat onboarding or menu changes are included.

Runtime gate: verify fresh first-play timestamp availability, Java/Bedrock width and simultaneous aNewbie/other boss bars, command discoverability, successful/failed use count display, exhaustion, reconnect and reload/disable cleanup. A local test cannot establish client appearance or newcomer understanding.

## Pilot configuration proposal

Optional entry/home guidance: `onboarding.enabled: true` shows a first backend join Start Survival action whose click executes `/rtp` through normal checks. After the first successful RTP, a valid first-day newcomer with no named home, positive home limit and sethome permission gets a readable `/sethome base` / `/home base` explanation. Java clicking suggests the home command; the player submits it voluntarily. Existing RTP counter prevents repeat guidance across relog/reload; no extra persistence or auto-created home. Messages are configurable and opt-in remains false by default. First-day prompt gating uses elapsed backend first-play time independently of protection and allowance configuration.

Separate Hub source adds an optional Start Survival hotbar item at a nonreserved slot. Production selector action was read-only verified as `[PROXY] SMP`, selector slot 0, PvP sword slot 2. Hub proposed settings: start_survival.enabled true, server SMP, slot 4, select_on_join true. Review/merge/build/deployment and Java/Bedrock acceptance remain required in both repositories. Test without Floodgate click support as well: readable commands remain usable. Validate no repeat home prompt on successful second RTP, no prompt after failure and no item overwrite/build-mode transfer.

Protected destinations: `rtp.excluded-regions: [warzone, spawn, market]` checks the live WorldGuard region objects after safe-location adjustment and at warmup completion. No rank/member/admin teleport bypass exemption. Source locations remain allowed, including spawn, so RTP can start survival. Missing provider/manager/named region blocks RTP; explicit empty list opts out. This restriction is independent of the newcomer pilot. Verify authorized isolated runtime inside/on/outside each boundary, safe-search shifts, region resize during warmup, provider unavailability, no use consumed on rejection, and spawn-to-wilderness success before production activation.

Apply only after review, merge, canonical build, deployment authorization and runtime acceptance. Preserve all other production settings:

```yaml
rtp:
  newcomer:
    enabled: true
    max-uses: 3
    window-seconds: 86400
```

This is three total uses, including any already recorded uses. It is not daily replenishment, three extra uses, or a new persistence counter. Eligibility uses Paper's first-play timestamp on this backend, including offline time. The bonus does not extend aNewbie protection. Missing/future timestamps get no bonus. Existing higher finite limits and existing negative computed limits are retained. Existing rank-resolution semantics are not changed by this patch.

Disable `rtp.newcomer.enabled` to roll back the policy through the normal approved config/reload path. Preserve `rtp_uses.yml`; players who have used more than their resulting normal limit remain exhausted. No resetting or deleting player records is part of rollback.

## Measurement gate

Start a fresh baseline after confirming the resumed SMP collector against actual joins; do not backfill missing sessions as zero-length visits. Use UUIDs across Hub and SMP. Exclude identified staff/test accounts and automated traffic, but do not infer bots solely from short sessions. Keep Java/Bedrock and acquisition source separate when those fields are available.

Measure weekly newcomer cohorts using one documented America/New_York date boundary:

1. New network UUIDs reaching SMP within ten minutes / eligible new network UUIDs.
2. New SMP UUIDs accumulating at least 15 minutes of non-AFK SMP play in their first 24 hours / eligible new SMP UUIDs.
3. SMP first sessions under two minutes, combining reconnects within two minutes and distinguishing successful Hub transfer from quit. Server stops, forced disconnects, ongoing sessions and unknown failure reasons must be reported separately.
4. Exact return windows: SMP activity in hours 24-48 and hours 168-192 after first network join, using only cohorts old enough to observe the full window. Label these D1 and D7 definitions explicitly.
5. Successful RTP uses, failures/cancellations, first-home creation and voluntary newcomer help, where actual event evidence is available. Do not invent these events from Plan's aggregate pages.
6. Active prime-time concurrency and AFK occupancy separately.

Collect at least seven complete days of baseline and fourteen pilot days, with counts as well as percentages. At the current approximately eight newcomers/day, small results remain noisy. Compare mature cohorts and traffic-source mix; a before/after change is directional evidence, not causal proof. Do not scale paid acquisition on incomplete SMP data.

## Runtime acceptance before activation

- Test fresh ordinary Java and Bedrock accounts, including fresh-account `getFirstPlayed()` behavior. If first-play time is unavailable at initial join, investigate the platform lifecycle before enabling; the implementation deliberately fails closed.
- Confirm effective RTP permission, limit 3 within the window and unchanged higher rank limits. Relog/restart must preserve used totals.
- Confirm successful first/second/third teleports and denial of a fourth, including queued searches and zero-warmup bypass profiles.
- Cancel by movement/combat and force an unsafe destination or timeout: usage must not increase.
- Verify expiry while queued, disabling the pilot, server restart and persistence flush behavior. Existing teleport warmup/permission/combat checks remain in force.
- Verify first join -> Hub selector -> SMP spawn -> RTP -> `/sethome base` -> `/home base` without privileged permissions. Record time to playable survival and errors on both editions.

## Next increment

After observing actual fresh-player journeys, prepare a separate Hub/spawn change for a prominent Start Survival action and concise prompts for RTP, setting a home, protection limits and raiding. Avoid a forced tutorial. Community welcome coverage and newcomer guild recruitment follow once entry and measurement are reliable. No messages, scheduled work or production changes are authorized by this document.
