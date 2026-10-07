# Newcomer RTP pilot verification

## October 7 activation preparation (RET-007)

- Fresh authoritative main: be5cef1cb1f47c1b1ff6035b72a832f26127ccb3, canonical PR18 merged; external PR16 superseded/closed without merging. Isolated branch codex/newcomer-activation-plan-20261007; existing clean retention-teleport checkout preserved.
- Read-only production inspection: latest SMP log enabled EnthusiaTeleport 1.2.10 at 00:19:20 server log time. Config read around 11:52 UTC retains one default use, configured region exclusions, 1000-block spawn/750-block recent-RTP spacing, three-use/86400-second newcomer settings, and both enable flags false. Selected snapshot in docs/pilots/newcomer-rtp/observed-settings.yml.
- Local PyYAML 6.0.3 parse of observed/activation/rollback YAML passed. Flattened observed-to-proposed comparison has exactly rtp.newcomer.enabled false -> true and onboarding.enabled false -> true; three-use/86400-second/boss-bar settings remain the same. Applying rollback restores every key in the selected observed snapshot. This is not a diff of a newly fetched full production config.
- Source config.yml defaults remain disabled. Plugin descriptor permission names and source prompt/first-success gates inspected against current main. git diff --check passed.
- Documentation/configuration preparation requires no new behavioral engine or red test; no Java source, runtime resources, version, build ownership or production file changes. Existing canonical validation is historical evidence below, not a newly executed suite. No project-local EARS/state tooling exists.
- Full artifact provenance, monorepo pin/combined build, hosted preparation checks/review and all isolated Java/Bedrock acceptance rows remain pending. No claim of production activation, successful client journey or improved retention. No production writes, reloads, restarts or uploads.

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
