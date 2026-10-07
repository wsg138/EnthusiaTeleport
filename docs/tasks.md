# Retention pilot tasks

## October 7 activation preparation (RET-007)

- [x] SPEC: fetch current canonical main be5cef1; preserve old checkouts; define two-flag configuration scope and separate pending Hub/Tags releases.
- [x] PROVE: read-only production startup enables Teleport 1.2.10; inspected config leaves newcomer and onboarding disabled. PR18 merged and supersedes closed/unmerged PR16. No artifact hash or live client acceptance inferred.
- [x] ENGINE/ARCH: no new runtime code needed; explicit partial activation/rollback overlays preserve persistent counters, source defaults and integration boundaries.
- [x] REFINE: PyYAML 6.0.3 parses all three files; flattened comparison finds only two enable-flag changes; rollback restores the selected observed baseline; source defaults remain disabled; git diff --check passes. No runtime suite rerun for this documentation-only change.
- [ ] REVIEW: publish preparation PR; inspect exact-head hosted checks/review independently of existing merged feature validation.
- [ ] RUNTIME/RELEASE: verify artifact provenance/monorepo pin, complete isolated Java/Bedrock acceptance, obtain separate production activation authorization and collect mature cohorts.

Older task entries below describe their historical external PR state; current feature source was merged through canonical PR18. They do not establish deployment authorization or client acceptance.

- [x] SPEC: inspect authoritative main and write RET-001..003. Clean isolated clone of wsg138/EnthusiaTeleport, origin/main 2dbc81995e96e7e43041a12905ea5f1c4dad4aa3, branch codex/newcomer-rtp-pilot. Original dirty Hub checkout preserved.
- [x] PROVE: new policy tests initially failed test compilation because NewcomerRtpPolicy did not exist (newcomer-red.log). The existing 17 tests passed on Java 23 targeting Java 21 before implementation. This is new-feature absence evidence, not fabricated historical regression evidence.
- [x] ENGINE: disabled-by-default configurable newcomer allowance uses the existing counter; queued searches recheck the limit after chunk lookup.
- [x] ARCH: original RtpSettings constructor retained; pure domain policy owns no Paper/provider/persistence dependencies; existing permission, rank resolution, combat checks, queue budgets, safe-location logic and successful-teleport callback retained.
- [x] RET-004 SPEC/PROVE: user requested aNewbie-style on-screen discovery. Read-only production config confirms action bar and green boss bar enabled. Prior PR had no on-screen notice (source absence evidence; no fabricated red test).
- [x] RET-004 ENGINE/ARCH: separate Adventure boss bar on existing main-thread task coordinator; permission/window/count gates, owned-bar cleanup, compatible newcomer constructor and configurable text. No companion API or action-bar writes.
- [x] REFINE-local: 31 tests passed with Maven clean verify, zero failures/errors/skips; five notice tests plus config option coverage. Git whitespace check passed. See docs/verification.md.
- [ ] REVIEW: upstream PR #16 published; exact-head checks/review inspected in the delivery turn. Hosted CI and review approval remain pending. No merge authorization.
- [ ] RUNTIME: merged-source/monorepo release checks, explicitly authorized deployment, fresh Java/Bedrock acceptance, baseline and pilot observation. Not established by local tests.

- [x] RET-005 SPEC/PROVE: current main re-fetched unchanged; clean ongoing branch preserved. Source lacked region exclusion; production read-only region file confirms world regions warzone/spawn/market. No fabricated historical red test. Optional clarification distinguishes destinations from origins; destination exclusion is stated assumption.
- [x] RET-005 ENGINE/ARCH: main-thread WorldGuard adapter checks configured live region geometry after safe search and again at warmup execution. No region membership/bypass exception; missing data fails closed. Additive guarded TeleportManager entry preserves existing callers and success accounting.
- [x] RET-005 REFINE: 36 tests pass on clean verify; four region/provider tests plus delayed execution rejection regression, updated descriptor contract. API signatures checked against production-version published WG 7.0.19/WE 7.4.4 artifacts; compile API 7.0.17 keeps Java 21 compatibility. Exact-head hosted checks remain separate.

- [x] RET-006 SPEC/PROVE: existing source has no Start Survival join action or first-success home prompt. Existing SetHomeCommand requires a name, so actual guidance uses `/sethome base`. Source absence evidence, not fabricated red tests.
- [x] RET-006 ENGINE/ARCH: optional platform prompts, real `/rtp` action preserves command checks, `/sethome base` only suggested, first-success marker reuses persistent RTP counter; no extra writes/home creation.
- [x] RET-006 REFINE: clean verify 40 tests pass, zero failures/errors/skips. Four new checks cover persistent suppression after reconstruction, existing homes/permissions/zero limit, timestamp/disabled gates and command click semantics. Hosted review and runtime remain pending.

- [x] REVIEW-refine: Codacy exposed ten exact-head findings on 4f65e5a. Split boss-bar eligibility/time/display responsibilities, remove null reassignment, extract first-day gate, remove test reflection with package-internal typed search access and normalize constants. Documented narrow PMD suppressions retain main-thread HashMap and compatibility constructor rather than changing thread/API semantics. Added same-UUID reconnect ownership regression. Latest clean verify passes 41 tests; hosted reanalysis/approval remains pending.

No project-local EARS validator or SPEAR state helper exists in this repository. The existing SPEAR Paper brownfield workflow was located and read; this requirement/task/evidence record is maintained without claiming unavailable tooling passed.

- [x] REVIEW-refine follow-up: four Codacy findings on dd254f4 resolved with named minute/hour constants and deduplicated permission/warzone test constants. Behavior unchanged; clean verify passes 41 tests. CI approval attempted for Teleport Build/Sentinel and Hub verification; GitHub rejected all three because repository admin rights are required. Hosted analysis on the new head remains a separate gate.
