# Newcomer RTP pilot verification

## Source and local checks

- Canonical repository: https://github.com/wsg138/EnthusiaTeleport ; default main fetched and inspected at 2dbc81995e96e7e43041a12905ea5f1c4dad4aa3.
- Isolated branch: codex/newcomer-rtp-pilot. No edits to prior Hub or monorepo checkouts.
- Maven 3.9.11, JDK 23, compiler release 21, declared Paper API 1.21.11-R0.1-SNAPSHOT.
- Initial offline dependency resolution lacked JetBrains annotations 26.0.2-1; online resolution succeeded. JDK 25 then failed the unchanged Mockito/Byte Buddy fixture. JDK 23 passed the existing 17 tests; no mocking dependency upgrade or experimental VM flag was introduced.
- PROVE: newcomer-red.log captured missing-policy compilation errors before policy implementation.
- Focused policy green: three tests passed (newcomer-green.log).
- Original allowance `mvn -B -ntp -o clean verify`: 26 tests, zero failures/errors/skips (pilot-final-verify.log).
- On-screen follow-up `mvn -B -ntp -o clean verify`: 31 tests, zero failures/errors/skips (notice-final-verify.log). Five notice tests cover updating counts, higher/unlimited effective limits, permission loss, exact expiry, disabled gates/invalid timestamps and quit/clear cleanup. Config tests cover notice default and opt-out under the still-default-off pilot.
- Initial clean test compilation hit a JDK 23 diagnostic-formatting failure with a deprecated PlayerQuitEvent constructor in the new fixture. A mocked event removed that fixture call; the full clean build succeeded. This is test/toolchain refinement, not gameplay regression evidence.
- New coverage: disabled/invalid/unknown eligibility; exact expiry; higher finite and negative computed limits; config defaults and safe duration bounds; existing UUID counts retained through flush/reconstruction and disabling/re-enabling; legacy constructor; exhausted queued search rejected before destination/teleport access.
- `git diff --check`: passed.
- Latest protected-region follow-up: Maven clean verify passes 36 tests, zero failures/errors/skips (region-final-verify.log). Covers production cuboid inclusive edges/flooring, independent shapes/live resizing, missing region/manager/provider, and denial after a simulated region change during warmup with no teleport or successful-use callback.
- Onboarding follow-up clean verify: 40 tests, zero failures/errors/skips (onboarding-final-verify.log). Four new checks cover persistent first-success suppression after reconstruction, home/permission/limit gating, valid-window enforcement and run-versus-suggest command click semantics. A failed warmup still never executes the successful-use callback. Platform messages are dispatched only by the existing successful callback or known first backend join.
- Review refinement clean verify: 41 tests, zero failures/errors/skips (onboarding-review-final-verify.log), including a new same-UUID reconnect ownership/cleanup regression. Codacy's ten exact-head findings on 4f65e5a addressed via smaller UI/window methods, no null reassignment, typed package-internal test access and static/deduplicated test constants. Two narrow documented PMD suppressions retain the main-thread-only map and required compatibility constructor; neither changes gameplay/API semantics. Hosted reanalysis remains a separate gate.
- The original worker's local snapshot artifact is intentionally not release evidence; canonical owner-side validation below supersedes it.

Region integration: production read-only file inventory shows worldguard-bukkit-7.0.19.jar and FastAsyncWorldEdit-Paper-2.15.4.jar. Live world regions warzone (-219,-64,-405)..(219,319,189), spawn (-49,78,-34)..(69,319,84), market (-73,-64,-282)..(102,319,-163). Coordinates are evidence/test fixtures only; implementation queries live region objects.

Attempting compilation directly against WG 7.0.19/WE 7.4.4 failed on their Java 25 class-file versions under JDK 23. Compile against provided WG 7.0.17/WE 7.3.18 to preserve declared Java 21 target. JDK 25 javap on published 7.0.19/7.4.4 artifacts confirmed signatures for WorldGuard.getInstance/getPlatform, getRegionContainer, RegionContainer.get(World), RegionManager.getRegion(String), ProtectedRegion.contains(BlockVector3) and BukkitAdapter.adapt(Bukkit World). This proves API signature compatibility, not acceptance on the live FAWE runtime. No API classes are shaded. WorldGuard soft dependency orders startup; absent/disabled provider fails closed when exclusions are configured. Explicit empty excluded-regions list opts out. Required regions must exist in the chosen RTP world or RTP fails closed.

The exact new policy initially had no implementation; adapter/config tests were added during refinement. The queued-search fixture now uses package-internal typed search access without reflection after Codacy review; it verifies early rejection before destination access, not actual Paper asynchronous chunk scheduling.

## Integration and runtime boundaries

No Companion-plugin compile-time API was changed. CombatLogX remains authoritative and RTP permission remains opt-in. No aNewbie API call, protection modification, database schema, use-count reset, or new persistence writer was added. Safety, warmup and successful-use accounting remain in the existing platform adapter.

Plan data collection was inspected through authenticated read-only UI. SMP recorded an additional session and increasing playtime/mob kills. That confirms current collection activity, not complete historical coverage, UUID funnel reconciliation, player departure causes or client acceptance. Documentation/measurement observations require no behavioral engine work; the policy change has separate behavioral proof and tests above.

Project-local EARS/state tools are absent; no automated SPEAR-tool result is claimed. Requirements/tasks/evidence are maintained in docs/requirements.md, docs/tasks.md and this file.

The monorepo owns release builds through plugins/enthusia-teleport. Its pin must be updated through a separate normal PR after canonical merge, with combined checks and clean merged-source build evidence before any authorized production upload. No pin, deployment, reload, restart, merge, player communication or automation was performed here.

Fresh-account first-play timestamps, effective permissions, queue/warmup runtime behavior, failed/cancelled teleports, expiry/rollback and Java/Bedrock player journeys remain explicit runtime acceptance gates in docs/retention-pilot.md. Local checks do not establish retention improvement.

## Canonical owner review and revalidation (2026-10-07)

The external feature head was ported exactly onto current `wsg138/EnthusiaTeleport` main after the bed-home fix and state/persistence test hardening, then reviewed as PR #18. Owner review found and fixed a pre-existing RTP rank bug: the configured `enthusia.rtp.unlimited: -1` permission could never win the old max-only comparison. A regression test now proves a matching negative rank limit grants unlimited access over finite defaults/ranks.

Trusted same-repository validation at head `1ae8a8f378a839b83113995640c1414ce9f2b2d7` passed Build run 107 and Sentinel artifact run 20. Maven executed 62 tests with zero failures, errors, or skips and reported `BUILD SUCCESS`. Codacy reported zero new issues. Release metadata was then finalized from `1.2.10-SNAPSHOT` to `1.2.10`; the final release head is required to pass the same trusted workflows before merge.

## Historical four-finding review follow-up (2026-10-05)

Codacy on dd254f4 reported two time-format numeric conditional warnings and duplicate permission/warzone test strings. Named constants preserve identical arithmetic, display text, permission and region values. Existing behavioral tests provide regression coverage; no new behavioral requirement or historical red test is claimed for constant extraction. Maven 3.9.11/JDK 23 clean verify passes 41 tests with zero failures/errors/skips (codacy-four-verify.log). git diff --check passed. That worker-local artifact hash is historical only and is not canonical release provenance.

At that historical fork head, GitHub-hosted jobs required approval and did not execute. This limitation is superseded by the 2026-10-07 same-repository validation above; no success is claimed for those blocked fork runs.

## Bed-first death respawns (1.2.11)

A player with an active, usable Minecraft bed should respawn there by default.
A player can select server spawn and later restore bed respawns. A missing or
obstructed bed uses configured spawn. Existing named bed homes, regular homes
and the completed import marker must survive the change.

The implementation keeps Paper's validated death destination when it is a bed,
and uses the existing spawn resolver for fallback. The opt-out and one-time
join tip use namespaced player data. The command never writes the bed/home
stores or changes the player's vanilla spawn point. The existing global
respawn override remains the operator control.

Before implementation the valid-bed regression failed because the listener
replaced the event location. The no-bed fallback control passed. Java 21
Maven clean verify subsequently passed 69 tests, including preference
commands, missing beds, the global override and existing persistence tests.
Live checks were read-only. In-game death, restart and join-message acceptance
remain pending.

## 1.2.12 home persistence audit fixes

The three regression cases failed on the previous implementation. A delayed
async save removed the second home, malformed YAML had no backup, and reload
lost the in-memory home after a failed write. The existing-format fixture
passed before the changes.

The home writer now serializes file replacement and discards superseded
requests and callbacks. Failed writes stay pending. Reload checks home
persistence before changing the rest of the plugin state. Strict YAML
loading preserves malformed input in a separate backup before replacement.

Java 21 Maven clean verify passed 74 tests with zero failures, errors or
skips. The tests cover both blocking and asynchronous write failure, preserve
existing home fields and owner identity across a save and reload, and verify
the existing bed file bytes, bed lookup and completed import marker. Tests
used temporary fixture files. Production installation and in-game acceptance
remain pending.

## 1.2.13 personal homes menu and completion

Two pre-change command tests reproduced empty personal suggestions and the
admin-only handling of a named personal destination. The updated tests cover
case-insensitive completion, bed permission filtering, named home and bed
dispatch, the explicit bed selector, and opening and clicking a bed-only menu.
Java 21 Maven clean verify passed 78 tests. The change reads existing homes and
beds through their managers. No storage changes or migration are required.

## 1.2.14 independent anchor home

A successful charged respawn-anchor binding records one personal anchor in
player persistent data. A later bed binding leaves that anchor available.
The active vanilla respawn block controls death unless the player opts into
server spawn. Named homes and bed homes keep their existing formats and limits.

The 1.2.13 baseline failed the anchor death regression. All 82 tests passed
under Java 21 on the 1.2.14 build. Coverage includes three distinct inventory
icons, click dispatch, tab and command name collisions, charge validation,
and independent anchor storage. The test server reported 1.2.14 after restart.
FainNeito bound a four-charge Nether anchor on SMP test. The homes menu
showed the named home, bed and anchor together. Clicking the anchor and
executing the tab-completed command from the Overworld both returned the
player to the anchor with four charges remaining. After death in the
Overworld, the player respawned beside the anchor with three charges left.
