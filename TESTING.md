# EnthusiaTeleport testing guide

Handwritten regression tests belong in this repository. Sentinel Sim is a separate built-plugin/runtime compatibility layer; it should not contain the repository's unit tests.

## Current test-hardening additions

This branch expands the repository beyond the previous two focused policy tests (`CombatTeleportPolicyTest` and `BedAccessPolicyTest`).

### `TeleportRequestManagerTest`
Tests internal request-bookkeeping behavior without starting a Paper server:

- incoming lookup and most-recent request selection;
- returned request collections are defensive copies;
- removing a request cleans both outgoing/incoming indexes;
- expiry task cancellation occurs on removal;
- removing a player clears both outgoing and incoming edges while preserving unrelated requests;
- null removal is a safe no-op.

Mockito is test-only and is used for Bukkit `Player`/`BukkitTask` interfaces. The test injects request records into the manager's private indexes because request creation itself schedules a real Bukkit task; runtime scheduling remains a Paper/Sentinel boundary.

### `OfflineNameCacheTest`
Protects the deterministic in-memory portion of tab-completion caching:

- joined player names are added;
- names are sorted case-insensitively;
- case-insensitive duplicates are not added;
- prefix matching is case-insensitive;
- null prefix means no prefix filter.

The async `usercache.json` refresh path and `offlineOnly=true` Bukkit lookup require a runtime/static-Bukkit layer and are not claimed by this unit test.

### `PerformanceMonitorTest`
Protects counter semantics:

- null/blank keys and zero amounts are ignored;
- increments and signed additions accumulate correctly;
- missing counters read as zero;
- snapshot/summary output is deterministic and key-sorted.

### `PluginSurfaceContractTest`
Freezes the reviewed operator-facing surface:

- exact command names;
- every command retains description, usage and explicit permission;
- every command permission is declared;
- privileged permissions remain operator-only by default;
- RTP remains explicit opt-in (`default: false`);
- CombatLogX remains a hard dependency;
- NewPlayerProtection remains a soft integration;
- Paper API version remains explicit.

A change here may be intentional, but the test forces the command/security/provider surface to be reviewed instead of changing silently.

## Existing tests retained

- `CombatTeleportPolicyTest` — combat restriction/bypass policy.
- `BedAccessPolicyTest` — persistent-bed access policy.

## Running tests

Java 21 and the checked-in Maven project are expected.

Full gate:

```bash
mvn --batch-mode --no-transfer-progress clean test package
```

Focused examples:

```bash
mvn -Dtest=TeleportRequestManagerTest test
mvn -Dtest=OfflineNameCacheTest test
mvn -Dtest=PerformanceMonitorTest test
mvn -Dtest=PluginSurfaceContractTest test
mvn -Dtest=CombatTeleportPolicyTest,BedAccessPolicyTest test
```

## Results

Surefire:

- `target/surefire-reports/*.txt`
- `target/surefire-reports/TEST-*.xml`

GitHub Actions build/Sentinel-artifact workflows provide hosted exact-head evidence. Record the PR head SHA with any claimed result.

## Failure triage

1. **Request-manager failure** — check symmetric outgoing/incoming cleanup, expiry-task ownership, duplicate removal and disconnect/reload semantics. Do not leave a request in only one index.
2. **Manifest/security failure** — decide whether the command/provider/permission change is intentional. Privilege expansion must be explicit and reviewed.
3. **Cache/counter failure** — fix deterministic behavior or the test only when the contract intentionally changed.
4. **Harness failure** — dependency resolution, Mockito, Maven or compilation failed before the assertion. Fix the harness without weakening behavior.
5. **Sentinel/runtime failure** — repository tests pass but the built plugin/dependencies fail lifecycle or simulation. Preserve that evidence separately.
6. **Infrastructure failure** — zero-step/no-runner GitHub failure is neither a product failure nor a pass.

## Important remaining test gaps

This repository is still substantially under-tested. Priority future suites should cover:

- `TeleportManager`: warmup, movement cancellation, cooldown, external-state cancellation, world/combat restrictions, force/bypass, disconnect and task cleanup;
- `TeleportRequestManager`: actual scheduled expiry, `/tpahere` combat cancellation and online-player notifications under MockBukkit/real Paper;
- `HomeManager` / `HomeGuiManager`: create/update/delete, rank limits, persistence, reload and GUI stale-state behavior;
- `BedHomeManager`: persistence, rename/delete/list/manage, broken/missing bed behavior and restart recovery beyond the pure access policy;
- `RtpManager`: quotas, queue bounds, spacing, timeout, chunk-load rate limits, safe-location rejection and cancellation;
- `SafeLocationFinder`: hazards, world height/border, liquids, insufficient headroom and edge terrain;
- `BackManager` / `LastLocationManager`: ordering, max history, persistence and offline location recovery;
- `IgnoreManager`: idempotent ignore/unignore, persistence and request rejection;
- `InventoryViewCommand`: view/edit permissions, stale targets and inventory close/reopen behavior;
- `SpawnManager`: configured spawn, first-join kit/bed behavior and respawn override;
- config reload/validation and malformed configuration;
- admin logging queue/flush bounds;
- CombatLogX and NewPlayerProtection provider-present/provider-missing/version behavior.

These should be added incrementally as deterministic unit/MockBukkit tests. Do **not** fake CombatLogX/BlueSlimeCore artifacts or weaken Sentinel provenance just to obtain a runtime pass.

## Choosing the right layer

- Pure policies/value transformations: JUnit.
- Bukkit object behavior that MockBukkit models faithfully: MockBukkit/integration tests.
- Built artifact loading and generated player sequences: Sentinel.
- CombatLogX/NPP integration, real chunk generation/teleport safety, or Paper behavior not modeled honestly: real Paper/live-like test harness.

## Security and privacy

Never commit production player databases, homes, teleport history, IPs, CombatLogX data, secrets, server credentials or production config dumps. Use generated UUIDs, mocks and temporary fixtures.

## Worker coordination

Reconcile live `main`, open PRs and changed paths before edits. This test-hardening PR is test/documentation-only. If a new test exposes a real product defect, fix it in the correct owning branch/PR rather than silently mixing production changes into this branch.
