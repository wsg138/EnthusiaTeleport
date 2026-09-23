# EnthusiaTeleport testing guide

Handwritten regression tests belong in this repository. Sentinel Sim is a separate built-plugin/runtime compatibility layer; it should not contain the repository's unit tests.

## Current automated coverage

### Request/cache/performance/surface hardening

The first owner-directed hardening pass added:

- `TeleportRequestManagerTest` — symmetric request indexes, most-recent selection, defensive snapshots, task cancellation and player-removal cleanup;
- `OfflineNameCacheTest` — join-cache deduplication/sorting and case-insensitive prefix matching;
- `PerformanceMonitorTest` — counter validation, accumulation and deterministic output;
- `PluginSurfaceContractTest` — exact command, permission, dependency/provider and API-version surface;
- the pre-existing `CombatTeleportPolicyTest` and `BedAccessPolicyTest` remain the pure policy baselines.

### State and persistence hardening

The second owner-directed hardening pass adds:

#### `BackManagerTest`

Covers deterministic `/back` history semantics without booting Paper:

- bounded LIFO ordering and oldest-entry eviction;
- same-block duplicate suppression;
- identical coordinates in different worlds remain distinct;
- recorded locations and `peek()` results are defensive clones;
- null locations/players and worldless locations are ignored;
- `backMax <= 0` disables history;
- reload applies a new maximum to subsequent records;
- removing one player does not disturb another player's history.

#### `RtpManagerStateTest`

Covers the deterministic RTP quota/persistence layer without running random world/chunk searches:

- YAML load of valid usage counters;
- negative persisted counters are clamped to zero;
- invalid UUID keys are ignored;
- usage increments mark persistence work;
- blocking flush round-trips through `rtp_uses.yml`;
- default RTP limits and rank-specific increases;
- highest matching rank wins;
- finite quota is allowed strictly below the limit and rejected at/above it;
- negative limit means unlimited use.

#### `HomeManagerTest`

Covers deterministic home state and YAML persistence:

- case/whitespace-normalized lookup keys;
- overwriting a home preserves original display name and creation timestamp while updating coordinates/orientation;
- case-insensitive delete and owner-scoped clear;
- rank-specific limits only raise the default and the highest matching limit wins;
- `isOverLimit` becomes true only after count exceeds the limit;
- blocking flush/reload round-trips home identity, world, coordinates, yaw/pitch and creation time;
- null/missing lookup behavior is safe.

Mockito is test-only. Temporary directories and generated UUIDs are used for persistence tests; no production player data or live server files are used.

## Running tests

Java 21 and the Maven project are expected.

Full gate:

```bash
mvn --batch-mode --no-transfer-progress clean test package
```

Focused examples:

```bash
mvn -Dtest=BackManagerTest test
mvn -Dtest=RtpManagerStateTest test
mvn -Dtest=HomeManagerTest test
mvn -Dtest=TeleportRequestManagerTest,OfflineNameCacheTest,PerformanceMonitorTest test
mvn -Dtest=PluginSurfaceContractTest,CombatTeleportPolicyTest,BedAccessPolicyTest test
```

## Results

Surefire writes:

- `target/surefire-reports/*.txt`
- `target/surefire-reports/TEST-*.xml`

GitHub Actions Build and Sentinel-artifact workflows provide hosted exact-head evidence. Record the PR head SHA with any claimed result. A skipped, cancelled, zero-step, or older-SHA run is not a pass.

## Failure triage

1. **Back-history failure** — inspect ordering, deduplication, max-depth trimming, world identity, clone ownership, reload and disconnect cleanup. Do not replace real `Location` semantics with string-only assertions.
2. **Home-state/persistence failure** — inspect normalized keys, display-name/timestamp preservation, rank limits, owner isolation and YAML round-trip behavior. Never use production `homes.yml` as a fixture.
3. **RTP quota/persistence failure** — inspect persisted counter normalization, rank/default maximum selection, exact quota boundary and YAML round-trip. Do not confuse these unit tests with actual RTP world-search validation.
4. **Request-manager failure** — check symmetric outgoing/incoming cleanup, expiry-task ownership, duplicate removal and disconnect/reload semantics.
5. **Manifest/security failure** — decide whether command/provider/permission changes are intentional. Privilege expansion must be explicit and reviewed.
6. **Cache/counter failure** — fix deterministic behavior or the test only when the contract intentionally changed.
7. **Harness failure** — dependency resolution, Mockito, Maven or compilation failed before the assertion. Fix the harness without weakening behavior.
8. **Sentinel/runtime failure** — repository tests pass but the built plugin/dependencies fail lifecycle or simulation. Preserve that evidence separately.
9. **Infrastructure failure** — zero-step/no-runner GitHub failure is neither a product failure nor a pass.

## Important remaining test gaps

The deterministic state layer is materially stronger, but runtime-heavy behavior still needs additional coverage:

- `TeleportManager`: warmup, movement cancellation, cooldown, external-state cancellation, world/combat restrictions, force/bypass, disconnect and task cleanup;
- `TeleportRequestManager`: actual scheduled expiry, `/tpahere` combat cancellation and online-player notifications under MockBukkit/real Paper;
- `HomeGuiManager`: GUI navigation, stale-state behavior, permission changes while open and safe close/reopen behavior;
- `BedHomeManager`: persistence, rename/delete/list/manage, broken/missing bed behavior and restart recovery beyond the pure access policy;
- `RtpManager`: queue bounds, spacing, timeout, chunk-load rate limits, safe-location rejection, cancellation and recent-location spacing in a real/mock runtime;
- `SafeLocationFinder`: hazards, world height/border, liquids, insufficient headroom and edge terrain;
- `LastLocationManager`: persistence and offline location recovery/backstop scanning;
- `IgnoreManager`: idempotent ignore/unignore, persistence and request rejection;
- `InventoryViewCommand`: view/edit permissions, stale targets and inventory close/reopen behavior;
- `SpawnManager`: configured spawn, first-join kit/bed behavior and respawn override;
- config reload/validation and malformed configuration;
- admin logging queue/flush bounds;
- CombatLogX and NewPlayerProtection provider-present/provider-missing/version behavior.

These should be added incrementally at the layer that can model them honestly. Do **not** fake CombatLogX/BlueSlimeCore artifacts or weaken Sentinel provenance just to obtain a runtime pass.

## Choosing the right layer

- Pure policies, state transitions and value transformations: JUnit.
- File/YAML state with no live Bukkit scheduler requirement: JUnit + temporary directories + mocks.
- Bukkit behavior that MockBukkit models faithfully: MockBukkit/integration tests.
- Built artifact loading and generated player sequences: Sentinel.
- CombatLogX/NPP integration, real chunk generation/teleport safety, or Paper behavior not modeled honestly: real Paper/live-like test harness.

## Security and privacy

Never commit production player databases, homes, teleport history, IPs, CombatLogX data, secrets, server credentials or production config dumps. Use generated UUIDs, mocks and temporary fixtures.

## Worker coordination

Reconcile live `main`, open PRs and changed paths before edits. Test-hardening PRs are test/documentation-only unless the owner explicitly changes scope. If a new test exposes a real product defect, fix it in the correct owning product branch/PR rather than silently mixing production changes into a test branch.

The Sentinel artifact-producer PR may own `.github/workflows/sentinel-artifact.yml`; test hardening must not edit or overwrite that workflow.
